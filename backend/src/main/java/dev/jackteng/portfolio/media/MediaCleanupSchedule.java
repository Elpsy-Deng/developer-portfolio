package dev.jackteng.portfolio.media;

import dev.jackteng.portfolio.admin.MediaMaintenance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration @EnableScheduling
@ConditionalOnProperty(name="portfolio.media.scheduled-cleanup",havingValue="true",matchIfMissing=true)
public class MediaCleanupSchedule {
    private final MediaMaintenance cleanup;
    public MediaCleanupSchedule(MediaMaintenance cleanup) { this.cleanup=cleanup; }
    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void reclaim() {
        try { cleanup.reclaim(); }
        catch(RuntimeException ignored) { /* Durable retired rows remain retryable; admin endpoint reports failure. */ }
    }
}
