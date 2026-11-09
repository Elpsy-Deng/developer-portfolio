package dev.elpsy.portfolio.media;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class LocalImageStore implements ImageStore {
    private final Path root,objects,incoming;
    public LocalImageStore(@Value("${portfolio.media.root}") String directory) {
        root=Path.of(directory).toAbsolutePath().normalize(); objects=root.resolve("objects");incoming=root.resolve("incoming");
        try {
            rejectSymlinks(root);
            if(Files.exists(root) && !Files.exists(root.resolve(".portfolio-media-v1"))) {
                try(var entries=Files.list(root)) { if(entries.findAny().isPresent()) throw new IOException("Unowned store"); }
            }
            privateDirectory(root); privateDirectory(objects); privateDirectory(incoming);
            Path marker=root.resolve(".portfolio-media-v1");
            if(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS)) Files.writeString(marker,"portfolio-media-v1\n",StandardOpenOption.CREATE_NEW);
            guard();
        } catch(IOException e) { throw new ImageStorageProblem(e); }
    }
    private static void privateDirectory(Path path) throws IOException {
        Files.createDirectories(path);rejectSymlinks(path);
        if(Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rwx------"));
    }
    private static void rejectSymlinks(Path path) throws IOException {
        for(Path part=path;part!=null;part=part.getParent()) if(Files.isSymbolicLink(part)) throw new IOException("Symbolic path refused");
    }
    private void guard() throws IOException {
        rejectSymlinks(root);rejectSymlinks(objects);rejectSymlinks(incoming);
        Path marker=root.resolve(".portfolio-media-v1");
        if(!Files.isDirectory(objects,LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(incoming,LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(marker) || !Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)
                || !Files.readString(marker).equals("portfolio-media-v1\n")) throw new IOException("Unowned store");
    }
    private Path path(UUID id,String format) {
        if(id==null || !Set.of("png","jpg").contains(format)) throw new ImageStorageProblem();
        return objects.resolve(id+"."+format);
    }
    public void put(UUID id,String format,byte[] bytes) {
        Path destination=path(id,format), temporary=incoming.resolve(id+".part");
        try {
            guard();
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Key already exists");
            try(var channel=FileChannel.open(temporary,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                if(Files.getFileStore(temporary).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rw-------"));
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true);
            }
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
        } catch(IOException e) { throw new ImageStorageProblem(e); }
        finally { try { guard(); if(!Files.isSymbolicLink(temporary)) Files.deleteIfExists(temporary); } catch(IOException ignored) { /* Incoming files are never served. */ } }
    }
    public byte[] read(UUID id,String format) {
        try {
            guard();var file=path(id,format);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || Files.size(file)>10485760) throw new IOException("Missing image");
            try(var channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer=ByteBuffer.allocate(Math.toIntExact(channel.size()));
                while(buffer.hasRemaining()) if(channel.read(buffer)<0) throw new IOException("Truncated image");
                return buffer.array();
            }
        } catch(IOException|ArithmeticException e) { throw new ImageStorageProblem(e); }
    }
    public void delete(UUID id,String format) {
        try { guard();Path file=path(id,format);if(Files.isSymbolicLink(file)) throw new IOException("Symbolic file refused");Files.deleteIfExists(file); }
        catch(IOException e) { throw new ImageStorageProblem(e); }
    }
    public int clearIncoming(Instant cutoff,int limit) {
        try {
            guard();int removed=0;
            try(var entries=Files.newDirectoryStream(incoming)) {
                for(Path file:entries) {
                    if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                    if(Files.getLastModifiedTime(file,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) {
                        Files.delete(file);if(++removed>=limit) break;
                    }
                }
            }
            return removed;
        } catch(IOException e) { throw new ImageStorageProblem(e); }
    }
    public List<StoredFile> oldFiles(Instant cutoff,int limit) {
        try {
            guard();var result=new ArrayList<StoredFile>();
            try(var entries=Files.newDirectoryStream(objects)) {
                for(Path file:entries) {
                    String name=file.getFileName().toString();
                    if(!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)")) continue;
                    if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                    if(Files.getLastModifiedTime(file,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) {
                        result.add(new StoredFile(UUID.fromString(name.substring(0,36)),name.substring(37)));
                        if(result.size()>=limit) break;
                    }
                }
            }
            return result;
        } catch(IOException e) { throw new ImageStorageProblem(e); }
    }
}
