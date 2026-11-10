package dev.jackteng.portfolio.credentials;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import dev.jackteng.portfolio.auth.AdminIdentity;
import dev.jackteng.portfolio.auth.PeerAddresses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/password")
public class PasswordController {
    private final PasswordChange password;
    private final PeerAddresses peers;
    public PasswordController(PasswordChange password,PeerAddresses peers){this.password=password;this.peers=peers;}
    @GetMapping ResponseEntity<?> profile(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(password.profile((AdminIdentity)auth.getPrincipal()));}
    @PostMapping ResponseEntity<Void> change(@RequestBody JsonNode command,Authentication auth,HttpServletRequest request){
        password.change(command,(AdminIdentity)auth.getPrincipal(),peers.address(request));
        var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler(PasswordChange.Limited.class) ResponseEntity<?> limited(PasswordChange.Limited error){
        return ResponseEntity.status(429).header("Retry-After",Integer.toString(error.retry)).cacheControl(CacheControl.noStore())
                .body(Map.of("code",error.code(),"message",error.getMessage()));
    }
}
