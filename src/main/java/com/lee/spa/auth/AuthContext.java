package com.lee.spa.auth;

import java.util.Arrays;

/** Request-scoped identity, populated exclusively after token validation. */
public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();
    private AuthContext() { }
    public static AuthPrincipal current() {
        AuthPrincipal principal = CURRENT.get();
        if (principal == null) throw new AuthException(401, "请先登录");
        return principal;
    }
    public static Long requireUserId() { return current().userId(); }
    public static AuthPrincipal requireRole(String... roles) {
        AuthPrincipal principal = current();
        if (Arrays.stream(roles).noneMatch(principal.role()::equals)) throw new AuthException(403, "无权执行此操作");
        return principal;
    }
    public static void set(AuthPrincipal principal) { CURRENT.set(principal); }
    public static void clear() { CURRENT.remove(); }
}
