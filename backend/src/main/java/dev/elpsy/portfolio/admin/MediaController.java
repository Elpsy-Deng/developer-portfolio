package dev.elpsy.portfolio.admin;

import java.util.UUID;
import dev.elpsy.portfolio.auth.AdminIdentity;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/admin")
public class MediaController {
    private final MediaProjects media;
    private final MediaMaintenance cleanup;
    public MediaController(MediaProjects media,MediaMaintenance cleanup) { this.media=media;this.cleanup=cleanup; }
    @GetMapping("/projects/{slug}/media") ResponseEntity<?> list(@PathVariable String slug) { return ok(media.list(slug)); }
    @PostMapping(value="/projects/{slug}/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<?> upload(@PathVariable String slug,@RequestParam Long expectedVersion,@RequestParam String kind,
            @RequestParam(defaultValue="") String title,@RequestParam(defaultValue="") String description,@RequestParam String alt,
            @RequestPart("file") MultipartFile file,@AuthenticationPrincipal AdminIdentity actor) {
        return ok(media.upload(slug,expectedVersion,kind,title,description,alt,file,actor));
    }
    @PutMapping("/projects/{slug}/media/{id}")
    ResponseEntity<?> update(@PathVariable String slug,@PathVariable UUID id,@RequestBody MediaProjects.Metadata command,@AuthenticationPrincipal AdminIdentity actor) {
        return ok(media.update(slug,id,command,actor));
    }
    @DeleteMapping("/projects/{slug}/media/{id}")
    ResponseEntity<?> remove(@PathVariable String slug,@PathVariable UUID id,@RequestBody AdminProjects.Delete command,@AuthenticationPrincipal AdminIdentity actor) {
        return ok(media.remove(slug,id,command,actor));
    }
    @PostMapping("/media/cleanup") ResponseEntity<?> reclaim() { return ok(cleanup.reclaim()); }
    private ResponseEntity<?> ok(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
}
