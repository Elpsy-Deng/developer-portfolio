package dev.elpsy.portfolio.auth;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after CSRF and before password authentication. */
final class LoginProtectionFilter extends OncePerRequestFilter {
    private final LoginLimits limits;
    private final ObjectMapper json;
    private final PeerAddresses peers;
    private final Semaphore passwordWork = new Semaphore(2);
    LoginProtectionFilter(LoginLimits limits, PeerAddresses peers, ObjectMapper json) { this.limits = limits; this.peers=peers; this.json = json; }
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !request.getServletPath().equals("/api/admin/login");
    }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String username = request.getParameter("username"), password = request.getParameter("password");
        boolean valid = username != null && username.length() <= 64 && !username.isBlank()
                && password != null && !password.isEmpty() && password.length() <= 256;
        int retry = limits.admit(peers.address(request), valid ? username : "<invalid>");
        if (retry > 0) { limited(response, retry); return; }
        if (!valid) { AuthResponses.error(json, response, 401, "INVALID_CREDENTIALS"); return; }
        if (!passwordWork.tryAcquire()) { limited(response, 1); return; }
        try { chain.doFilter(request, response); }
        finally { passwordWork.release(); }
    }
    private void limited(HttpServletResponse response, int seconds) throws IOException {
        response.setHeader("Retry-After", Integer.toString(seconds));
        AuthResponses.error(json, response, 429, "LOGIN_RATE_LIMITED");
    }
}
