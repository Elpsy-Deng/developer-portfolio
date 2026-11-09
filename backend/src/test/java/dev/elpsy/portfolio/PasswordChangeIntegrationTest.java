package dev.elpsy.portfolio;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import dev.elpsy.portfolio.auth.*;
import dev.elpsy.portfolio.credentials.PasswordChange;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"portfolio.test-mode=true","portfolio.auth.cookie-secure=false","portfolio.media.scheduled-cleanup=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PasswordChangeIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordChange service;
    @Autowired LoginLimits limits;
    @Autowired @Qualifier("credentialsJdbc") JdbcTemplate credentials;
    @Autowired @Qualifier("identityJdbc") JdbcTemplate identity;
    @Autowired @Qualifier("authJdbc") JdbcTemplate auth;
    @Autowired @Qualifier("writerJdbc") JdbcTemplate writer;
    @Autowired JdbcTemplate reader;
    String prefix="pwd_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
    String admin=prefix+"_admin",viewer=prefix+"_viewer";
    final String oldPassword=UUID.randomUUID().toString(),newPassword=UUID.randomUUID().toString();
    JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD")));}
    @BeforeEach void provision(){cleanup();owner().update("DELETE FROM admin_login_bucket");String hash=encoder.encode(oldPassword);owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN'),(?,?,'VIEWER')",admin,hash,viewer,hash);}
    @AfterAll void cleanup(){owner().update("DELETE FROM spring_session WHERE principal_name LIKE ?",prefix+"%");owner().update("DELETE FROM admin_identity_audit WHERE username LIKE ?",prefix+"%");owner().update("DELETE FROM admin_account WHERE username LIKE ?",prefix+"%");}
    class Browser {
        CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> call(String method,String path,Object body,String csrf)throws Exception{
            var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/"+path)).timeout(Duration.ofSeconds(15));
            if(csrf!=null)r.header("X-CSRF-TOKEN",csrf);
            if(body!=null)r.header("Content-Type",body instanceof String?"application/x-www-form-urlencoded":"application/json");
            String content=body==null?"":body instanceof String s?s:json.writeValueAsString(body);
            return client.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(content)).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode session()throws Exception{var r=call("GET","session",null,null);assertEquals(200,r.statusCode());return json.readTree(r.body());}
        String csrf()throws Exception{return session().path("csrf").path("token").asText();}
        HttpResponse<String> login(String name,String secret)throws Exception{return call("POST","login","username="+name+"&password="+secret,csrf());}
        void login(String name)throws Exception{assertEquals(204,login(name,oldPassword).statusCode());}
        HttpResponse<String> change(long version,String current,String next)throws Exception{return call("POST","password",Map.of("expectedVersion",version,"currentPassword",current,"newPassword",next),csrf());}
    }
    long auditCount(){return owner().queryForObject("SELECT count(*) FROM admin_identity_audit WHERE username LIKE ?",Long.class,prefix+"%");}
    @Test void bothRolesCanChangeOnlyOwnPasswordAndAllSessionsAreRevoked()throws Exception{
        for(String name:List.of(admin,viewer)){
            var b=new Browser();b.login(name);var second=new Browser();second.login(name);
            var profile=b.call("GET","password",null,null);assertEquals(200,profile.statusCode());assertFalse(profile.body().contains("hash"));assertFalse(profile.body().contains(oldPassword));
            var changed=b.change(1,oldPassword,newPassword);assertEquals(204,changed.statusCode());assertTrue(changed.body().isEmpty());assertEquals("no-store",changed.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(0,owner().queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?",Integer.class,name));
            assertEquals(401,second.call("GET","overview",null,null).statusCode());assertFalse(b.session().path("authenticated").asBoolean());
            assertEquals(401,b.login(name,oldPassword).statusCode());assertEquals(204,b.login(name,newPassword).statusCode());
            assertEquals(2,owner().queryForObject("SELECT version FROM admin_account WHERE username=?",Integer.class,name));
        }
        assertEquals(2,auditCount());
    }
    @Test void wrongPasswordAndReuseLeaveHashSessionsVersionAndAuditUnchanged()throws Exception{
        var b=new Browser();b.login(admin);String hash=owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?",String.class,admin);
        var wrong=b.change(1,"wrong",newPassword);assertEquals(400,wrong.statusCode());assertEquals("PASSWORD_INVALID",json.readTree(wrong.body()).path("code").asText());
        assertEquals(400,b.change(1,oldPassword,oldPassword).statusCode());assertTrue(b.session().path("authenticated").asBoolean());
        assertEquals(hash,owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?",String.class,admin));assertEquals(1,owner().queryForObject("SELECT version FROM admin_account WHERE username=?",Integer.class,admin));assertEquals(0,auditCount());
    }
    @Test void anonymousAndMissingCsrfCannotReachPasswordWorkOrCounter()throws Exception{
        var b=new Browser();assertEquals(401,b.call("GET","password",null,null).statusCode());
        b.login(viewer);int buckets=owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class);
        assertEquals(403,b.call("POST","password",Map.of("expectedVersion",1,"currentPassword",oldPassword,"newPassword",newPassword),null).statusCode());
        assertEquals(buckets,owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class));assertEquals(0,auditCount());
    }
    @Test void bodyCannotChooseAnotherUserOrCoerceVersionOrSubmitWeakPassword()throws Exception{
        var b=new Browser();b.login(viewer);
        for(Map<String,Object> cmd:List.<Map<String,Object>>of(Map.of("expectedVersion",1,"currentPassword",oldPassword,"newPassword",newPassword,"username",admin),Map.of("expectedVersion","1","currentPassword",oldPassword,"newPassword",newPassword),Map.of("expectedVersion",1,"currentPassword",12,"newPassword",newPassword),Map.of("expectedVersion",1,"currentPassword",oldPassword,"newPassword","short")))
            assertEquals(400,b.call("POST","password",cmd,b.csrf()).statusCode());
        assertEquals(409,b.change(2,oldPassword,newPassword).statusCode());assertEquals(0,auditCount());
        assertEquals(1,owner().queryForObject("SELECT version FROM admin_account WHERE username=?",Integer.class,admin));
    }
    @Test void persistedPasswordQuotaDoesNotResetOnLoginAndWindowExpiryRecovers()throws Exception{
        var b=new Browser();b.login(viewer);
        for(int i=0;i<5;i++)assertEquals(400,b.change(1,"wrong",newPassword).statusCode());
        assertEquals(204,b.login(viewer,oldPassword).statusCode());
        var limited=b.change(1,oldPassword,newPassword);assertEquals(429,limited.statusCode());assertEquals("PASSWORD_RATE_LIMITED",json.readTree(limited.body()).path("code").asText());assertTrue(Integer.parseInt(limited.headers().firstValue("Retry-After").orElseThrow())>0);
        owner().update("UPDATE admin_login_bucket SET window_start=window_start-301");assertEquals(204,b.change(1,oldPassword,newPassword).statusCode());
    }
    @Test void passwordPeerGlobalLimitsAreSeparateFromLoginAndCannotGrowUnbounded() {
        for(int i=0;i<20;i++)assertEquals(0,limits.admitPassword("192.0.2.1","synthetic_"+i));
        assertTrue(limits.admitPassword("192.0.2.1","another")>0);assertEquals(0,limits.admit("192.0.2.1",viewer));
        for(int i=0;i<20;i++)assertEquals(0,limits.admitPassword("192.0.2.2","other_"+i));
        assertTrue(limits.admitPassword("192.0.2.3","new")>0);assertTrue(owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class)<100);
    }
    @Test void concurrentChangesHaveExactlyOneWinnerAndOnePasswordChangeAudit()throws Exception{
        var a=new Browser();a.login(viewer);var b=new Browser();b.login(viewer);String ca=a.csrf(),cb=b.csrf();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->{gate.await();return a.call("POST","password",Map.of("expectedVersion",1,"currentPassword",oldPassword,"newPassword",newPassword),ca).statusCode();});
            var y=pool.submit(()->{gate.await();return b.call("POST","password",Map.of("expectedVersion",1,"currentPassword",oldPassword,"newPassword",newPassword+"b"),cb).statusCode();});gate.countDown();
            int one=x.get(15,TimeUnit.SECONDS),two=y.get(15,TimeUnit.SECONDS);assertEquals(1,(one==204?1:0)+(two==204?1:0));assertTrue(Set.of(401,409,429).contains(one==204?two:one));
            assertEquals(204,new Browser().login(viewer,one==204?newPassword:newPassword+"b").statusCode());
        }
        assertEquals(2,owner().queryForObject("SELECT version FROM admin_account WHERE username=?",Integer.class,viewer));assertEquals(1,auditCount());
    }
    @Test void auditFailureRollsBackHashVersionAndEverySessionDeletion()throws Exception{
        var b=new Browser();b.login(viewer);var another=new Browser();another.login(viewer);
        String before=owner().queryForObject("SELECT to_jsonb(a)::text FROM admin_account a WHERE username=?",String.class,viewer);
        try{owner().execute("REVOKE INSERT ON admin_identity_audit FROM portfolio_credentials");
            var r=b.change(1,oldPassword,newPassword);assertEquals(503,r.statusCode());assertEquals("AUTH_UNAVAILABLE",json.readTree(r.body()).path("code").asText());
            assertEquals(before,owner().queryForObject("SELECT to_jsonb(a)::text FROM admin_account a WHERE username=?",String.class,viewer));assertTrue(b.session().path("authenticated").asBoolean());assertTrue(another.session().path("authenticated").asBoolean());assertEquals(0,auditCount());
        }finally{owner().execute("GRANT INSERT ON admin_identity_audit TO portfolio_credentials");}
    }
    @Test void staleRoleAndDisabledActorCannotChangePasswordEvenThroughService() {
        var actor=new AdminIdentity(viewer,null,"VIEWER",1,true);
        for(String statement:List.of("UPDATE admin_account SET enabled=false WHERE username=?","UPDATE admin_account SET enabled=true,role='ADMIN',version=version+1 WHERE username=?")){
            owner().update(statement,viewer);
            var r=assertThrows(dev.elpsy.portfolio.catalog.CatalogProblem.class,()->service.change(json.valueToTree(Map.of("expectedVersion",1,"currentPassword",oldPassword,"newPassword",newPassword)),actor,"192.0.2.1"));assertEquals(401,r.status());
        }
        assertEquals(0,auditCount());
    }
    @Test void credentialWriterCannotEscalateRolesCreateAccountsOrReadSessionsOrAudit()throws Exception{
        assertEquals("portfolio_credentials",credentials.queryForObject("SELECT current_user",String.class));
        for(String q:List.of("UPDATE admin_account SET role='ADMIN'","UPDATE admin_account SET enabled=true","INSERT INTO admin_account(username,password_hash,role) VALUES ('bad','bad','ADMIN')","DELETE FROM admin_account","SELECT * FROM spring_session_attributes","SELECT * FROM admin_identity_audit","UPDATE project SET title='bad'","CREATE TABLE bad(id int)"))assertThrows(org.springframework.dao.DataAccessException.class,()->credentials.execute(q));
        for(JdbcTemplate db:List.of(identity,auth,writer,reader))assertThrows(org.springframework.dao.DataAccessException.class,()->db.execute("UPDATE admin_account SET password_hash='bad'"));
        var b=new Browser();b.login(admin);assertEquals(204,b.change(1,oldPassword,newPassword).statusCode());b.login(admin,newPassword);
        String hash=owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?",String.class,admin);
        var raw=b.call("GET","accounts/audit",null,null);assertEquals(200,raw.statusCode());assertFalse(raw.body().contains(hash));assertFalse(raw.body().contains(oldPassword));assertFalse(raw.body().contains(newPassword));assertFalse(raw.body().contains("password"));assertTrue(raw.body().contains("PASSWORD_CHANGE"));
    }
}
