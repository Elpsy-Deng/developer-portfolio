package dev.jackteng.portfolio.media;

import java.util.*;
import java.security.*;
import dev.jackteng.portfolio.catalog.CatalogProblem;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ImageController {
    private final JdbcTemplate reader,writer;
    private final ImageStore store;
    public ImageController(JdbcTemplate reader,@Qualifier("writerJdbc") JdbcTemplate writer,ImageStore store) { this.reader=reader;this.writer=writer;this.store=store; }
    @GetMapping("/api/public/media/{id}") ResponseEntity<byte[]> publicImage(@PathVariable String id) { return read(id,true); }
    @GetMapping("/api/admin/media/{id}") ResponseEntity<byte[]> adminImage(@PathVariable String id) { return read(id,false); }
    private ResponseEntity<byte[]> read(String text,boolean published) {
        UUID id;
        try { id=UUID.fromString(text);if(!id.toString().equals(text)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e) { throw new CatalogProblem(404,"IMAGE_NOT_FOUND","找不到圖片"); }
        var rows=(published?reader:writer).query("""
            SELECT a.format,a.mime,a.bytes,a.sha256 FROM image_asset a WHERE a.id=? AND a.retired_at IS NULL
            AND EXISTS(SELECT 1 FROM project_media m JOIN project p ON p.id=m.project_id WHERE m.asset_id=a.id
            """+(published?" AND p.published ":"")+")",(r,n)->new Asset(r.getString(1),r.getString(2),r.getLong(3),r.getString(4)),id);
        if(rows.isEmpty()) throw new CatalogProblem(404,"IMAGE_NOT_FOUND","找不到圖片");
        var asset=rows.getFirst();byte[] bytes=store.read(id,asset.format());
        try { if(bytes.length!=asset.bytes() || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(asset.hash())) throw new ImageStorageProblem(); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(asset.mime()))
                .contentLength(bytes.length).header("Content-Disposition","inline; filename=\""+id+"."+asset.format()+"\"")
                .header("X-Content-Type-Options","nosniff").body(bytes);
    }
    private record Asset(String format,String mime,long bytes,String hash) {}
}
