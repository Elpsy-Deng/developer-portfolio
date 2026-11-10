package dev.jackteng.portfolio.admin;

import java.time.*;
import java.util.UUID;
import dev.jackteng.portfolio.media.ImageStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MediaMaintenance {
    private final JdbcTemplate db;
    private final ImageStore store;
    public MediaMaintenance(@Qualifier("writerJdbc") JdbcTemplate db,ImageStore store) { this.db=db;this.store=store; }
    public record Reclaimed(int retired,int orphaned,int temporary) {}
    @Transactional(transactionManager="writerTransactionManager",timeout=30)
    public Reclaimed reclaim() {
        db.queryForObject("SELECT pg_advisory_xact_lock(681227041)",Object.class);
        var retired=db.query("""
            SELECT a.id,a.format FROM image_asset a WHERE retired_at IS NOT NULL AND purged_at IS NULL
            AND NOT EXISTS(SELECT 1 FROM project_media m WHERE m.asset_id=a.id)
            ORDER BY retired_at,id LIMIT 50 FOR UPDATE
            """,(r,n)->new ImageStore.StoredFile(r.getObject(1,UUID.class),r.getString(2)));
        for(var asset:retired) {
            store.delete(asset.id(),asset.format());
            db.update("UPDATE image_asset SET purged_at=clock_timestamp() WHERE id=?",asset.id());
        }
        int orphans=0;
        for(var file:store.oldFiles(Instant.now().minus(Duration.ofHours(24)),50)) {
            if(db.queryForObject("SELECT count(*) FROM image_asset WHERE id=?",Integer.class,file.id())==0) {
                store.delete(file.id(),file.format());orphans++;
            }
        }
        return new Reclaimed(retired.size(),orphans,store.clearIncoming(Instant.now().minus(Duration.ofHours(24)),50));
    }
}
