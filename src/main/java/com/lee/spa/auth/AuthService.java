package com.lee.spa.auth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.lee.spa.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public record User(Long id, String nickname, String role, Long staffId) { }
    public record Login(String token, User user, LocalDateTime expiresAt, String mode) {
        @Override public String toString(){return "Login[token=redacted, mode="+mode+"]";}
    }
    private record Account(long id, User user, String password) {
        @Override public String toString(){return "Account[id="+id+", password=redacted]";}
    }

    @Transactional
    public Login adminLogin(String username, String password) {
        if (username == null || password == null) throw new AuthException(401, "账号或密码错误");
        String expectedName = environment.getProperty("spa.auth.admin-username", "admin");
        String expectedHash = environment.getProperty("spa.auth.admin-password-hash", "");
        String expectedPassword = environment.getProperty("spa.auth.admin-password", "");
        boolean valid = username.equals(expectedName) && ((!expectedHash.isBlank() && PasswordHasher.matches(password, expectedHash))
                || (expectedHash.isBlank() && expectedPassword.length() >= 12 && MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), expectedPassword.getBytes(StandardCharsets.UTF_8))));
        if (!valid) throw new AuthException(401, "账号或密码错误");
        Account account = findOrCreate("admin:" + username, "管理员", "ADMIN", username, null, null);
        if (!account.user.role().equals("ADMIN")) throw new AuthException(403, "管理员身份配置冲突");
        return issue(account, "ADMIN");
    }

    @Transactional
    public Login staffLogin(String username, String password) {
        List<Account> accounts = accounts("username=?", username);
        if (accounts.size() != 1 || password == null || !accounts.get(0).user.role().equals("STAFF")
                || !PasswordHasher.matches(password, accounts.get(0).password)) throw new AuthException(401, "账号或密码错误");
        return issue(accounts.get(0), "STAFF");
    }

    @Transactional
    public Login devLogin(String identity) {
        boolean local = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equals("local") || p.equals("test"));
        boolean production = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equals("prod") || p.equals("production"));
        if (!local || production || !environment.getProperty("spa.auth.dev-login-enabled", Boolean.class, false)) throw new AuthException(403, "开发登录未开放");
        if (identity == null || !identity.matches("[a-zA-Z0-9_-]{3,40}")) throw new BusinessException("开发用户标识需为3至40位字母、数字、下划线或短横线");
        return issue(findOrCreate("dev:" + identity, "开发体验用户", "USER", null, null, null), "DEVELOPMENT_SIMULATION");
    }

    @Transactional
    public Login wechatLogin(String code) {
        if (code == null || code.isBlank() || code.length() > 256) throw new BusinessException("微信登录凭证无效");
        String appId = environment.getProperty("spa.wechat.app-id", "");
        String secret = environment.getProperty("spa.wechat.app-secret", "");
        if (appId.isBlank() || secret.isBlank()) throw new BusinessException("微信登录暂未配置，请联系门店");
        Map<?, ?> result;
        try {
            var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000); factory.setReadTimeout(5000);
            result = RestClient.builder().requestFactory(factory).build().get()
                    .uri("https://api.weixin.qq.com/sns/jscode2session?appid={id}&secret={secret}&js_code={code}&grant_type=authorization_code", appId, secret, code)
                    .retrieve().body(Map.class);
        } catch (RuntimeException failure) { throw new BusinessException("微信登录服务暂不可用，请重试"); }
        if (result == null || !(result.get("openid") instanceof String openid) || openid.isBlank()) throw new BusinessException("微信登录凭证已过期，请重新登录");
        // No session_key persistence, response or logging.
        return issue(findOrCreate("wx:" + openid, "微信用户", "USER", null, null, openid), "WECHAT");
    }

    @Transactional
    public User createStaff(String username, String password, Long staffId) {
        AuthContext.requireRole("ADMIN");
        if (username == null || !username.matches("[a-zA-Z0-9_-]{3,60}") || password == null || password.length() < 12 || password.length() > 128 || staffId == null) throw new BusinessException("技师账号需合法用户名、至少12位密码和有效技师");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM staff WHERE id=? AND status=1", Long.class, staffId) != 1) throw new BusinessException("技师不存在或已停用");
        if (!accounts("username=?", username).isEmpty()) throw new BusinessException("用户名已存在");
        return findOrCreate("staff:" + username, "技师", "STAFF", username, PasswordHasher.hash(password), null, staffId).user;
    }

    private Account findOrCreate(String identity, String nickname, String role, String username, String password, String openid) {
        return findOrCreate(identity, nickname, role, username, password, openid, null);
    }
    private Account findOrCreate(String identity, String nickname, String role, String username, String password, String openid, Long staffId) {
        List<Account> found = accounts("identity_key=?", identity);
        if (!found.isEmpty()) return found.get(0);
        Long userId = null;
        if (openid != null) {
            List<Long> legacy = jdbc.query("SELECT id FROM spa_user WHERE openid=? AND status=1", (rs, row) -> rs.getLong(1), openid);
            if (!legacy.isEmpty()) userId = legacy.get(0);
        }
        long accountId = IdWorker.getId();
        if (userId == null) {
            userId = IdWorker.getId();
            jdbc.update("INSERT INTO spa_user(id,openid,nickname,status) VALUES(?,?,?,1)", userId, openid, nickname);
        }
        jdbc.update("INSERT INTO spa_account(id,user_id,identity_key,username,password_hash,nickname,role,staff_id) VALUES(?,?,?,?,?,?,?,?)", accountId, userId, identity, username, password, nickname, role, staffId);
        return new Account(accountId, new User(userId, nickname, role, staffId), password);
    }
    private List<Account> accounts(String condition, Object value) {
        return jdbc.query("SELECT a.* FROM spa_account a JOIN spa_user u ON u.id=a.user_id AND u.status=1 WHERE a.status=1 AND " + condition,
                (rs, row) -> new Account(rs.getLong("id"), new User(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("role"), rs.getObject("staff_id", Long.class)), rs.getString("password_hash")), value);
    }
    private Login issue(Account account, String mode) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiry = LocalDateTime.now(ZONE).plusHours(12);
        jdbc.update("INSERT INTO spa_auth_token(token_hash,account_id,expires_at) VALUES(?,?,?)", digest(token), account.id, Timestamp.valueOf(expiry));
        return new Login(token, account.user, expiry, mode);
    }
    public AuthPrincipal authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new AuthException(401, "登录凭证无效");
        List<AuthPrincipal> found = jdbc.query("SELECT a.user_id,a.role,a.staff_id FROM spa_auth_token t JOIN spa_account a ON a.id=t.account_id JOIN spa_user u ON u.id=a.user_id WHERE t.token_hash=? AND t.expires_at>? AND a.status=1 AND u.status=1",
                (rs, row) -> new AuthPrincipal(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class)), digest(token), Timestamp.valueOf(LocalDateTime.now(ZONE)));
        if (found.size() != 1) throw new AuthException(401, "登录已失效，请重新登录");
        return found.get(0);
    }
    public User me() { return accounts("user_id=?", AuthContext.requireUserId()).stream().findFirst().orElseThrow(() -> new AuthException(401, "用户不可用")).user; }
    public void logout(String token) { jdbc.update("DELETE FROM spa_auth_token WHERE token_hash=?", digest(token)); }
    public static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
