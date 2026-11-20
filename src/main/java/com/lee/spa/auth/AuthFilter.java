package com.lee.spa.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lee.spa.common.result.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

@Component
@Order(1)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {
    private final AuthService auth;
    private final ObjectMapper json;
    private static final Set<String> LOGIN = Set.of("/api/auth/admin-login", "/api/auth/staff-login", "/api/auth/wechat-login", "/api/auth/dev-login");
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        try {
            String header = request.getHeader("Authorization");
            if (!LOGIN.contains(path) && header != null) {
                if (!header.startsWith("Bearer ")) throw new AuthException(401, "登录凭证无效");
                AuthContext.set(auth.authenticate(header.substring(7)));
            }
            if (path.startsWith("/api/") && !LOGIN.contains(path)) {
                boolean publicRead = request.getMethod().equals("GET") && (path.equals("/api/services") || path.startsWith("/api/services/") || path.equals("/api/staff") || path.startsWith("/api/staff/") || path.equals("/api/operations/content"));
                if (!publicRead) AuthContext.current();
                if (path.startsWith("/api/admin/") || (path.contains("/weekly-rules/") && !request.getMethod().equals("GET"))) AuthContext.requireRole("ADMIN");
            }
            chain.doFilter(request, response);
        } catch (AuthException failure) {
            response.setStatus(failure.getStatus()); response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getWriter(), new Result<>(failure.getStatus(), failure.getMessage(), null));
        } finally { AuthContext.clear(); }
    }
}
