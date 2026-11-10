package dev.jackteng.portfolio.admin;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import dev.jackteng.portfolio.auth.AdminIdentity;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/taxonomy")
public class TaxonomyController {
    private final Taxonomy taxonomy;
    public TaxonomyController(Taxonomy taxonomy){this.taxonomy=taxonomy;}
    @GetMapping("/audit") ResponseEntity<?> audit(){return ok(200,taxonomy.audit());}
    @GetMapping("/{kind}") ResponseEntity<?> list(@PathVariable String kind){return ok(200,taxonomy.list(kind));}
    @PostMapping("/{kind}") ResponseEntity<?> create(@PathVariable String kind,@RequestBody JsonNode command,@AuthenticationPrincipal AdminIdentity actor){return ok(201,taxonomy.create(kind,command,actor));}
    @PutMapping("/{kind}/{id}") ResponseEntity<?> update(@PathVariable String kind,@PathVariable UUID id,@RequestBody JsonNode command,@AuthenticationPrincipal AdminIdentity actor){return ok(200,taxonomy.update(kind,id,command,actor));}
    @DeleteMapping("/{kind}/{id}") ResponseEntity<?> delete(@PathVariable String kind,@PathVariable UUID id,@RequestBody JsonNode command,@AuthenticationPrincipal AdminIdentity actor){taxonomy.delete(kind,id,command,actor);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    private ResponseEntity<?> ok(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
