package dev.elpsy.portfolio.auth;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Persisted login time, independent of JDBC last_access_time; old sessions without it must re-authenticate. */
final class AbsoluteSessionFilter extends OncePerRequestFilter {
    static final String LOGIN_TIME = "portfolio.auth.login-time";
    private final long maxAgeMillis;
    private final ObjectMapper json;
    AbsoluteSessionFilter(long seconds, ObjectMapper json) {
        if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("Absolute session lifetime must be 1..86400 seconds");
        this.maxAgeMillis = seconds * 1000; this.json = json;
    }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminIdentity) {
            var session = request.getSession(false);
            Object issued = session == null ? null : session.getAttribute(LOGIN_TIME);
            long now = System.currentTimeMillis();
            if (!(issued instanceof Long time) || time <= 0 || time > now || now - time >= maxAgeMillis) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
                String path = request.getServletPath();
                if (!path.equals("/api/admin/session") && !path.equals("/api/admin/login")) {
                    AuthResponses.error(json, response, 401, "UNAUTHENTICATED"); return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
