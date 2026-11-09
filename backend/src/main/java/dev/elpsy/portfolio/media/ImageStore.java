package dev.elpsy.portfolio.media;

import java.time.Instant;
import java.util.*;

/** Keys are server-generated UUIDs and normalized formats, never user filenames. */
public interface ImageStore {
    void put(UUID id,String format,byte[] bytes);
    byte[] read(UUID id,String format);
    void delete(UUID id,String format);
    int clearIncoming(Instant cutoff,int limit);
    List<StoredFile> oldFiles(Instant cutoff,int limit);
    record StoredFile(UUID id,String format) {}
}
