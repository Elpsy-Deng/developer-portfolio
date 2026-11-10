package dev.jackteng.portfolio.auth;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import dev.jackteng.portfolio.catalog.ProjectCatalog;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final ProjectCatalog catalog;
    private final AdminAccounts accounts;
    private final LoginLimits limits;
    private final long absoluteSeconds;
    public AdminController(ProjectCatalog catalog, AdminAccounts accounts, LoginLimits limits,
            @Value("${portfolio.auth.absolute-seconds:28800}") long absoluteSeconds) {
        this.catalog = catalog; this.accounts = accounts; this.limits = limits; this.absoluteSeconds = absoluteSeconds;
    }
    @GetMapping("/session")
    ResponseEntity<?> session(Authentication authentication, CsrfToken csrf) {
        AdminIdentity identity = authentication != null && authentication.getPrincipal() instanceof AdminIdentity p ? p : null;
        return ok(Map.of("authenticated", identity != null,
                "username", identity == null ? "" : identity.getUsername(), "role", identity == null ? "" : identity.role(),
                "csrf", Map.of("headerName", csrf.getHeaderName(), "token", csrf.getToken())));
    }
    @GetMapping("/overview")
    ResponseEntity<?> overview() { return ok(Map.of("publishedProjects", catalog.list("", "", "").catalogTotal())); }
    @GetMapping("/security")
    ResponseEntity<?> security() { return ok(Map.of("enabledAccounts", accounts.enabledAccounts(), "idleTimeoutMinutes", 30,
            "absoluteTimeoutSeconds", absoluteSeconds, "loginWindowSeconds", limits.windowSeconds(),
            "loginAccountLimit", limits.accountLimit(), "loginPeerLimit", limits.peerLimit(), "loginGlobalLimit", limits.globalLimit())); }
    private ResponseEntity<?> ok(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
