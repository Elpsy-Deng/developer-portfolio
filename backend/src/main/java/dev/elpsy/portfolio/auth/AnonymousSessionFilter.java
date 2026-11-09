package dev.elpsy.portfolio.auth;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs before CSRF's deferred token can persist a fresh JDBC Session. */
final class AnonymousSessionFilter extends OncePerRequestFilter {
    private final LoginLimits limits;
    private final PeerAddresses peers;
    private final ObjectMapper json;
    AnonymousSessionFilter(LoginLimits limits,PeerAddresses peers,ObjectMapper json){this.limits=limits;this.peers=peers;this.json=json;}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        try {
            if(request.getSession(false)==null){
                int retry=limits.admitAnonymous(peers.address(request));
                if(retry>0){response.setHeader("Retry-After",Integer.toString(retry));AuthResponses.error(json,response,429,"SESSION_RATE_LIMITED");return;}
            }
        } catch(DataAccessException error){AuthResponses.error(json,response,503,"AUTH_UNAVAILABLE");return;}
        chain.doFilter(request,response);
    }
}
