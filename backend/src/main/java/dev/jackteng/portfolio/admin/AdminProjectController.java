package dev.jackteng.portfolio.admin;

import dev.jackteng.portfolio.auth.AdminIdentity;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin")
public class AdminProjectController {
    private final AdminProjects projects;
    public AdminProjectController(AdminProjects projects) { this.projects=projects; }
    @GetMapping("/projects") ResponseEntity<?> list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String published,
            @RequestParam(defaultValue="1") String page,@RequestParam(defaultValue="12") String pageSize) { return ok(projects.list(q,category,published,dev.jackteng.portfolio.catalog.PageRequest.parse(page,pageSize))); }
    @GetMapping("/catalog-options") ResponseEntity<?> options() { return ok(projects.options()); }
    @GetMapping("/projects/{slug}") ResponseEntity<?> detail(@PathVariable String slug) { return ok(projects.detail(slug)); }
    @GetMapping("/projects/{slug}/audit") ResponseEntity<?> audit(@PathVariable String slug) { return ok(projects.audit(slug)); }
    @PostMapping("/projects") ResponseEntity<?> create(@RequestBody AdminProjects.Change command,@AuthenticationPrincipal AdminIdentity actor) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(projects.create(command,actor));
    }
    @PutMapping("/projects/{slug}") ResponseEntity<?> update(@PathVariable String slug,@RequestBody AdminProjects.Change command,@AuthenticationPrincipal AdminIdentity actor) {
        return ok(projects.update(slug,command,actor));
    }
    @DeleteMapping("/projects/{slug}") ResponseEntity<?> delete(@PathVariable String slug,@RequestBody AdminProjects.Delete command,@AuthenticationPrincipal AdminIdentity actor) {
        projects.delete(slug,command,actor); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private ResponseEntity<?> ok(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
