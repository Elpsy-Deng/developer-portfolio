package dev.jackteng.portfolio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** All replicas use the same short, serialized PostgreSQL admission transaction, outside password work. */
@Service
public class LoginLimits {
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final int window, account, peer, global, maxBuckets, changeAccount, changePeer, changeGlobal;
    private final int sessionPeer,sessionGlobal;
    public LoginLimits(@Qualifier("authJdbc") JdbcTemplate db,
            @Qualifier("authTransactionManager") PlatformTransactionManager manager,
            @Value("${portfolio.auth.login-window-seconds:300}") int window,
            @Value("${portfolio.auth.login-account-limit:30}") int account,
            @Value("${portfolio.auth.login-peer-limit:60}") int peer,
            @Value("${portfolio.auth.login-global-limit:120}") int global,
            @Value("${portfolio.auth.login-max-buckets:4096}") int maxBuckets,
            @Value("${portfolio.auth.change-account-limit:5}") int changeAccount,
            @Value("${portfolio.auth.change-peer-limit:20}") int changePeer,
            @Value("${portfolio.auth.change-global-limit:40}") int changeGlobal,
            @Value("${portfolio.auth.session-peer-limit:120}") int sessionPeer,
            @Value("${portfolio.auth.session-global-limit:600}") int sessionGlobal, Environment environment) {
        if (window < 1 || window > 3600 || account < 1 || peer < 1 || global < 1
                || account > 10000 || peer > 10000 || global > 10000 || maxBuckets < 3 || maxBuckets > 100000)
            throw new IllegalArgumentException("Invalid login protection configuration");
        if (changeAccount < 1 || changePeer < 1 || changeGlobal < 1 || changeAccount > 10000 || changePeer > 10000 || changeGlobal > 10000)
            throw new IllegalArgumentException("Invalid password protection configuration");
        // A future trusted proxy integration must be explicit; silently rewriting remoteAddr is unsafe here.
        if (!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", "none")))
            throw new IllegalArgumentException("Forwarded headers are not trusted by this milestone");
        this.db = db; this.window = window; this.account = account; this.peer = peer; this.global = global; this.maxBuckets = maxBuckets;
        this.changeAccount=changeAccount; this.changePeer=changePeer; this.changeGlobal=changeGlobal;
        if(sessionPeer<1 || sessionGlobal<1 || sessionPeer>10000 || sessionGlobal>10000)
            throw new IllegalArgumentException("Invalid anonymous session protection configuration");
        this.sessionPeer=sessionPeer;this.sessionGlobal=sessionGlobal;
        this.transaction = new TransactionTemplate(manager); this.transaction.setTimeout(3);
    }
    private record Bucket(String key, int limit) {}
    /** Zero means admitted; positive seconds are a generic retry hint, without revealing which bucket blocked. */
    public int admit(String address, String username) {
        return admission(address,username,"",account,peer,global);
    }
    public int admitPassword(String address,String username) {
        return admission(address,username,"password:",changeAccount,changePeer,changeGlobal);
    }
    public int admitAnonymous(String address) {
        return admission(List.of(new Bucket(key("session:global"),sessionGlobal),new Bucket(key("session:peer:"+address),sessionPeer)),true);
    }
    private int admission(String address,String username,String namespace,int accountLimit,int peerLimit,int globalLimit) {
        var buckets = List.of(new Bucket(key(namespace+"global"), globalLimit), new Bucket(key(namespace+"peer:" + address), peerLimit),
                new Bucket(key(namespace+"account:" + username.strip().toLowerCase(Locale.ROOT)), accountLimit));
        return admission(buckets);
    }
    private int admission(List<Bucket> buckets) {
        return admission(buckets,false);
    }
    private int admission(List<Bucket> buckets,boolean waitForSession) {
        return transaction.execute(status -> {
            // New Session requests may overlap a login admission. Queue briefly rather than return
            // a spurious 429 while capacity is still available; the existing 3s transaction bounds it.
            // Login/password retain their original immediate overload refusal.
            if(waitForSession) db.execute("SELECT pg_advisory_xact_lock(74041306)");
            else if (!Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(74041306)", Boolean.class))) return 1;
            long now = db.queryForObject("SELECT floor(extract(epoch FROM clock_timestamp()))::bigint", Long.class);
            // Expired buckets are small/bounded; no separate cron or raw identity journal is needed.
            db.update("DELETE FROM admin_login_bucket WHERE window_start <= ?", now - window);
            int missing = 0, retry = 0;
            for (var bucket : buckets) {
                var rows = db.query("SELECT window_start, attempts FROM admin_login_bucket WHERE bucket_key=?",
                        (r,n) -> new long[]{r.getLong(1), r.getInt(2)}, bucket.key());
                if (rows.isEmpty()) missing++;
                else if (rows.getFirst()[1] >= bucket.limit())
                    retry = Math.max(retry, (int)Math.max(1, rows.getFirst()[0] + window - now));
            }
            if (retry > 0) return retry;
            if (db.queryForObject("SELECT count(*) FROM admin_login_bucket", Integer.class) + missing > maxBuckets) return window;
            for (var bucket : buckets) db.update("""
                    INSERT INTO admin_login_bucket(bucket_key,window_start,attempts) VALUES (?,?,1)
                    ON CONFLICT(bucket_key) DO UPDATE SET attempts=admin_login_bucket.attempts+1
                    """, bucket.key(), now);
            return 0;
        });
    }
    public int windowSeconds() { return window; }
    public int accountLimit() { return account; }
    public int peerLimit() { return peer; }
    public int globalLimit() { return global; }
    private static String key(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
