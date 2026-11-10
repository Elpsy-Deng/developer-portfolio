package dev.jackteng.portfolio.credentials;

import java.util.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.databind.*;
import dev.jackteng.portfolio.auth.*;
import dev.jackteng.portfolio.identity.AccountManagement.Account;
import dev.jackteng.portfolio.catalog.CatalogProblem;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordChange {
    public static class Limited extends CatalogProblem {
        final int retry;
        Limited(int retry){super(429,"PASSWORD_RATE_LIMITED","密碼更改嘗試過於頻繁，請稍後再試");this.retry=retry;}
    }
    private record Stored(Account account,String hash) {
        @Override public String toString(){return "Stored[redacted]";}
    }
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final LoginLimits limits;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Semaphore hashing=new Semaphore(2);
    public PasswordChange(@Qualifier("credentialsJdbc") JdbcTemplate db,PasswordEncoder encoder,LoginLimits limits,ObjectMapper json,
            @Qualifier("credentialsTransactionManager") PlatformTransactionManager manager) {
        this.db=db;this.encoder=encoder;this.limits=limits;this.json=json;tx=new TransactionTemplate(manager);tx.setTimeout(10);
    }
    private Stored stored(String username) {
        var rows=db.query("SELECT username,role,enabled,version,password_hash FROM admin_account WHERE username=?",
                (r,n)->new Stored(new Account(r.getString(1),r.getString(2),r.getBoolean(3),r.getLong(4)),r.getString(5)),username);
        if(rows.isEmpty())throw unauthorized();return rows.getFirst();
    }
    private static void valid(Account account,AdminIdentity actor) {
        if(!account.enabled() || account.version()!=actor.version() || !account.role().equals(actor.role()))throw unauthorized();
    }
    public Account profile(AdminIdentity actor) {
        var rows=db.query("SELECT username,role,enabled,version FROM admin_account WHERE username=?",
                (r,n)->new Account(r.getString(1),r.getString(2),r.getBoolean(3),r.getLong(4)),actor.getUsername());
        if(rows.isEmpty())throw unauthorized();var row=rows.getFirst();valid(row,actor);return row;
    }
    public void change(JsonNode command,AdminIdentity actor,String address) {
        if(command==null || !command.isObject() || command.size()!=3)throw invalid();
        command.fieldNames().forEachRemaining(k->{if(!Set.of("expectedVersion","currentPassword","newPassword").contains(k))throw invalid();});
        var version=command.get("expectedVersion");
        if(version==null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue()<1)throw invalid();
        String current=text(command,"currentPassword"),next=text(command,"newPassword");
        if(current.isEmpty() || current.length()>256 || next.length()<12 || next.length()>256 || next.isBlank())throw invalid();
        int retry=limits.admitPassword(address,actor.getUsername());if(retry>0)throw new Limited(retry);
        if(!hashing.tryAcquire())throw new Limited(1);
        try {
            var before=stored(actor.getUsername());valid(before.account(),actor);
            if(before.account().version()!=version.longValue())throw conflict();
            if(!encoder.matches(current,before.hash()))throw new CatalogProblem(400,"PASSWORD_INVALID","目前密碼不正確");
            if(current.equals(next))throw new CatalogProblem(400,"PASSWORD_REUSED","新密碼必須與目前密碼不同");
            String newHash=encoder.encode(next);
            tx.executeWithoutResult(status->{
                // Same lock as identity governance; recheck role/disable/version/hash after password work.
                if(!Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(74041307)",Boolean.class)))
                    throw new CatalogProblem(409,"PASSWORD_BUSY","身份資料正在更新，請重新核對");
                var now=stored(actor.getUsername());valid(now.account(),actor);
                if(now.account().version()!=version.longValue() || !now.hash().equals(before.hash()) || version.longValue()==Long.MAX_VALUE)throw conflict();
                if(db.update("UPDATE admin_account SET password_hash=?,version=version+1 WHERE username=? AND version=? AND password_hash=?",
                        newHash,actor.getUsername(),version.longValue(),before.hash())!=1)throw conflict();
                db.update("DELETE FROM spring_session WHERE principal_name=?",actor.getUsername());
                var after=new Account(actor.getUsername(),now.account().role(),true,version.longValue()+1);
                db.update("INSERT INTO admin_identity_audit(id,username,actor,action,before_state,after_state) VALUES (?,?,?,'PASSWORD_CHANGE',?::jsonb,?::jsonb)",
                        UUID.randomUUID(),actor.getUsername(),actor.getUsername(),json.valueToTree(now.account()).toString(),json.valueToTree(after).toString());
            });
        } finally {hashing.release();}
    }
    private static String text(JsonNode node,String name){var value=node.get(name);if(value==null || !value.isTextual())throw invalid();return value.textValue();}
    private static CatalogProblem unauthorized(){return new CatalogProblem(401,"UNAUTHENTICATED","請重新登入");}
    private static CatalogProblem conflict(){return new CatalogProblem(409,"PASSWORD_VERSION_CONFLICT","身份資料已更新，請重新核對");}
    private static CatalogProblem invalid(){return new CatalogProblem(400,"INVALID_PASSWORD_CHANGE","密碼欄位不正確；新密碼需12至256字元");}
}
