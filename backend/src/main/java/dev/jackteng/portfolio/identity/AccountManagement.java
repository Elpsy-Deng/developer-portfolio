package dev.jackteng.portfolio.identity;

import java.util.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.databind.*;
import dev.jackteng.portfolio.auth.AdminIdentity;
import dev.jackteng.portfolio.catalog.CatalogProblem;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountManagement {
    public record Account(String username,String role,boolean enabled,long version) {}
    public record Audit(UUID id,String username,String actor,String action,JsonNode before,JsonNode after,String createdAt) {}
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Semaphore hashing=new Semaphore(2);
    public AccountManagement(@Qualifier("identityJdbc") JdbcTemplate db,PasswordEncoder encoder,ObjectMapper json,
            @Qualifier("identityTransactionManager") PlatformTransactionManager manager) {
        this.db=db; this.encoder=encoder; this.json=json; tx=new TransactionTemplate(manager);tx.setTimeout(10);
    }
    public List<Account> list() { return db.query("SELECT username,role,enabled,version FROM admin_account ORDER BY username",(r,n)->new Account(r.getString(1),r.getString(2),r.getBoolean(3),r.getLong(4))); }
    private Account account(String name) {
        var rows=db.query("SELECT username,role,enabled,version FROM admin_account WHERE username=?",(r,n)->new Account(r.getString(1),r.getString(2),r.getBoolean(3),r.getLong(4)),name);
        if(rows.isEmpty()) throw problem(404,"ACCOUNT_NOT_FOUND","找不到帳號");return rows.getFirst();
    }
    private void actor(AdminIdentity identity) {
        // Serialize every governed identity mutation, including actor revalidation and last-ADMIN count.
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(74041307)",Boolean.class)))
            throw problem(409,"IDENTITY_BUSY","帳號管理正在更新，請重新核對後再試");
        Account now;
        try { now=account(identity.getUsername()); } catch(CatalogProblem e) { throw problem(401,"UNAUTHENTICATED","請重新登入"); }
        if(!now.enabled() || now.version()!=identity.version() || !now.role().equals(identity.role()))
            throw problem(401,"UNAUTHENTICATED","請重新登入");
        if(!now.role().equals("ADMIN")) throw problem(403,"FORBIDDEN","你沒有執行此操作的權限");
    }
    public Account create(JsonNode command,AdminIdentity identity) {
        fields(command,Set.of("username","password","role","enabled"));
        String name=text(command,"username"),password=text(command,"password"),role=role(command);
        if(!name.matches("[a-z][a-z0-9_-]{2,63}") || password.length()<12 || password.length()>256 || password.isBlank()) throw invalid();
        boolean enabled=enabled(command);
        if(!hashing.tryAcquire()) throw problem(409,"IDENTITY_BUSY","帳號管理正在更新，請稍後再試");
        String hash;
        try {hash=encoder.encode(password);} finally {hashing.release();}
        return tx.execute(status->{
            actor(identity);
            if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM admin_account WHERE username=?)",Boolean.class,name)))
                throw problem(409,"ACCOUNT_EXISTS","帳號代號已使用");
            if(db.queryForObject("SELECT count(*) FROM admin_account",Integer.class)>=100) throw problem(409,"ACCOUNT_LIMIT","最多建立100個管理帳號");
            db.update("INSERT INTO admin_account(username,password_hash,role,enabled) VALUES (?,?,?,?)",name,hash,role,enabled);
            var created=account(name);audit(identity,null,created,"CREATE");return created;
        });
    }
    public Account update(String name,JsonNode command,AdminIdentity identity) {
        if(!name.matches("[a-z][a-z0-9_-]{2,63}"))throw invalid();
        fields(command,Set.of("expectedVersion","role","enabled"));
        var v=command.get("expectedVersion");if(v==null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<1) throw invalid();
        String role=role(command);boolean enabled=enabled(command);
        return tx.execute(status->{
            actor(identity);var before=account(name);
            if(before.version()!=v.longValue())throw problem(409,"ACCOUNT_VERSION_CONFLICT","帳號已被更新，請核對最新版本");
            if(before.role().equals(role) && before.enabled()==enabled)return before;
            if(before.role().equals("ADMIN") && before.enabled() && (!role.equals("ADMIN") || !enabled)
                    && db.queryForObject("SELECT count(*) FROM admin_account WHERE role='ADMIN' AND enabled",Integer.class)<=1)
                throw problem(409,"LAST_ADMIN","必須保留至少一位啟用中的管理員");
            if(before.version()==Long.MAX_VALUE)throw problem(409,"ACCOUNT_VERSION_CONFLICT","帳號版本已達上限");
            db.update("UPDATE admin_account SET role=?,enabled=?,version=version+1 WHERE username=?",role,enabled,name);
            db.update("DELETE FROM spring_session WHERE principal_name=?",name);
            var after=account(name);audit(identity,before,after,"UPDATE");return after;
        });
    }
    private void audit(AdminIdentity actor,Account before,Account after,String action) {
        db.update("INSERT INTO admin_identity_audit(id,username,actor,action,before_state,after_state) VALUES (?,?,?, ?,?::jsonb,?::jsonb)",
                UUID.randomUUID(),after.username(),actor.getUsername(),action,before==null?null:json.valueToTree(before).toString(),json.valueToTree(after).toString());
    }
    public List<Audit> audit() {
        return db.query("SELECT id,username,actor,action,before_state::text,after_state::text,created_at::text FROM admin_identity_audit ORDER BY created_at DESC,id DESC LIMIT 50",(r,n)->{
            try { return new Audit(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5)==null?null:json.readTree(r.getString(5)),json.readTree(r.getString(6)),r.getString(7)); }
            catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid identity audit",e);}
        });
    }
    private static void fields(JsonNode node,Set<String> required) {
        if(node==null || !node.isObject() || node.size()!=required.size())throw invalid();
        node.fieldNames().forEachRemaining(k->{if(!required.contains(k))throw invalid();});
    }
    private static String text(JsonNode node,String key){var v=node.get(key);if(v==null || !v.isTextual())throw invalid();return v.textValue();}
    private static String role(JsonNode node){String role=text(node,"role");if(!Set.of("ADMIN","VIEWER").contains(role))throw invalid();return role;}
    private static boolean enabled(JsonNode node){var v=node.get("enabled");if(v==null || !v.isBoolean())throw invalid();return v.booleanValue();}
    private static CatalogProblem invalid(){return problem(400,"INVALID_ACCOUNT","帳號欄位不正確；密碼需12至256字元");}
    private static CatalogProblem problem(int status,String code,String message){return new CatalogProblem(status,code,message);}
}
