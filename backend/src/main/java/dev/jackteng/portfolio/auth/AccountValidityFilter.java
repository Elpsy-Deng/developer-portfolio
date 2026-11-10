package dev.jackteng.portfolio.auth;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Revoke stale identities immediately, rather than trusting a role saved in an old session. */
final class AccountValidityFilter extends OncePerRequestFilter {
    private final AdminAccounts accounts;
    AccountValidityFilter(AdminAccounts accounts) { this.accounts = accounts; }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminIdentity previous) {
            boolean valid;
            try {
                var current = accounts.loadUserByUsername(previous.getUsername());
                valid = current.isEnabled() && current.version() == previous.version() && current.role().equals(previous.role());
            } catch (UsernameNotFoundException e) { valid = false; }
            if (!valid) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
