package dev.elpsy.portfolio.admin;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ImageCatalog {
    private final JdbcTemplate db;
    public ImageCatalog(@Qualifier("writerJdbc") JdbcTemplate db) { this.db=db; }
    /** Called only within the same writer transaction that removes/replaces references. */
    public void retireOrphans() {
        db.update("UPDATE image_asset a SET retired_at=clock_timestamp() WHERE retired_at IS NULL AND NOT EXISTS(SELECT 1 FROM project_media m WHERE m.asset_id=a.id)");
    }
}
