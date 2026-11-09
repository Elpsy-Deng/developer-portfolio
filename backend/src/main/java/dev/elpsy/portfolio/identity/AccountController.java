package dev.elpsy.portfolio.identity;

import com.fasterxml.jackson.databind.JsonNode;
import dev.elpsy.portfolio.auth.AdminIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/accounts")
public class AccountController {
    private final AccountManagement accounts;
    public AccountController(AccountManagement accounts){this.accounts=accounts;}
    @GetMapping ResponseEntity<?> list(){return ok(200,accounts.list());}
    @GetMapping("/audit") ResponseEntity<?> audit(){return ok(200,accounts.audit());}
    @PostMapping ResponseEntity<?> create(@RequestBody JsonNode command,Authentication auth){return ok(201,accounts.create(command,(AdminIdentity)auth.getPrincipal()));}
    @PutMapping("/{username}") ResponseEntity<?> update(@PathVariable String username,@RequestBody JsonNode command,Authentication auth,HttpServletRequest request){
        var actor=(AdminIdentity)auth.getPrincipal();var changed=accounts.update(username,command,actor);
        if(changed.username().equals(actor.getUsername()) && changed.version()!=actor.version()) {
            var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
        }
        return ok(200,changed);
    }
    private ResponseEntity<?> ok(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
