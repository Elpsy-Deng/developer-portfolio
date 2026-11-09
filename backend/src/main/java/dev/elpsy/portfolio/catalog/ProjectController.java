package dev.elpsy.portfolio.catalog;

import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class ProjectController {
    private final ProjectCatalog catalog;

    public ProjectController(ProjectCatalog catalog) { this.catalog = catalog; }

    @GetMapping("/projects")
    public ResponseEntity<ProjectCatalog.Listing> projects(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String featured,
            @RequestParam(defaultValue="1") String page,@RequestParam(defaultValue="12") String pageSize) {
        return read(catalog.list(q, category, featured,PageRequest.parse(page,pageSize)));
    }

    @GetMapping("/projects/{slug}")
    public ResponseEntity<Project> detail(@PathVariable String slug) { return read(catalog.detail(slug)); }

    @GetMapping("/projects/{slug}/next")
    public ResponseEntity<ProjectCatalog.Next> next(@PathVariable String slug){return read(catalog.next(slug));}

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() { return read(catalog.categories()); }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        catalog.checkDatabase();
        return read(Map.of("status", "UP", "catalog", "POSTGRESQL"));
    }

    private static <T> ResponseEntity<T> read(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
