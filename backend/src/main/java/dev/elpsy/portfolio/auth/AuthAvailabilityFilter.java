package dev.elpsy.portfolio.auth;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Wrap JDBC session loading AND committing, which occur outside controller advice. */
@Component @Order(Integer.MIN_VALUE + 40)
public class AuthAvailabilityFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    public AuthAvailabilityFilter(ObjectMapper json) { this.json = json; }
    protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/admin/"); }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try { chain.doFilter(request, response); }
        catch (DataAccessException | TransactionException | InternalAuthenticationServiceException e) {
            if (!response.isCommitted()) {
                response.reset(); AuthResponses.error(json, response, 503, "AUTH_UNAVAILABLE");
            } else throw e;
        }
    }
}
