package dev.jackteng.portfolio;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import dev.jackteng.portfolio.auth.AdminIdentity;
import dev.jackteng.portfolio.identity.AccountManagement;
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
class AccountGovernanceIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountManagement service;
    @Autowired @Qualifier("identityJdbc") JdbcTemplate identity;
    @Autowired @Qualifier("authJdbc") JdbcTemplate auth;
    @Autowired @Qualifier("writerJdbc") JdbcTemplate writer;
    @Autowired JdbcTemplate reader;
    String prefix="gov_"+UUID.randomUUID().toString().replace("-","").substring(0,10);
    String admin=prefix+"_admin",other=prefix+"_other",viewer=prefix+"_viewer";
    final String password=UUID.randomUUID().toString();
    JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD")));}
    @BeforeEach void prepare(){
        cleanup();owner().update("DELETE FROM admin_login_bucket");String hash=encoder.encode(password);
        owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN'),(?,?,'ADMIN'),(?,?,'VIEWER')",admin,hash,other,hash,viewer,hash);
    }
    @AfterAll void cleanup(){
        owner().update("DELETE FROM spring_session WHERE principal_name LIKE ?",prefix+"%");
        owner().update("DELETE FROM admin_identity_audit WHERE actor LIKE ? OR username LIKE ?",prefix+"%",prefix+"%");
        owner().update("DELETE FROM admin_account WHERE username LIKE ?",prefix+"%");
    }
    class Browser {
        CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> call(String method,String path,Object body,String csrf) throws Exception {
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/"+path)).timeout(Duration.ofSeconds(15));
            if(csrf!=null)req.header("X-CSRF-TOKEN",csrf);
            String encoded=body==null?"":body instanceof String b?b:json.writeValueAsString(body);
            if(body!=null)req.header("Content-Type",body instanceof String?"application/x-www-form-urlencoded":"application/json");
            return client.send(req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(encoded)).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode session() throws Exception {var r=call("GET","session",null,null);assertEquals(200,r.statusCode());return json.readTree(r.body());}
        String csrf() throws Exception{return session().path("csrf").path("token").asText();}
        void login(String name)throws Exception{assertEquals(204,call("POST","login","username="+name+"&password="+password,csrf()).statusCode());}
        HttpResponse<String> change(String name,long version,String role,boolean enabled)throws Exception{return call("PUT","accounts/"+name,Map.of("expectedVersion",version,"role",role,"enabled",enabled),csrf());}
    }
    long audits(){return owner().queryForObject("SELECT count(*) FROM admin_identity_audit WHERE actor LIKE ?",Long.class,prefix+"%");}
    @Test void createLoginAndAuditNeverExposePasswordsOrHashes()throws Exception{
        var b=new Browser();b.login(admin);String name=prefix+"_new";
        var made=b.call("POST","accounts",Map.of("username",name,"password",password,"role","VIEWER","enabled",true),b.csrf());assertEquals(201,made.statusCode());
        assertEquals(1,json.readTree(made.body()).path("version").asLong());assertEquals("no-store",made.headers().firstValue("Cache-Control").orElseThrow());
        var login=new Browser();login.login(name);assertEquals(403,login.call("GET","accounts",null,null).statusCode());
        String hash=owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?",String.class,name);
        for(String path:List.of("accounts","accounts/audit")){
            var response=b.call("GET",path,null,null);assertEquals(200,response.statusCode());
            assertFalse(response.body().contains(password));assertFalse(response.body().contains(hash));assertFalse(response.body().contains("password"));
        }
        var raw=owner().queryForObject("SELECT after_state::text FROM admin_identity_audit WHERE username=?",String.class,name);
        assertEquals(Set.of("username","role","enabled","version"),json.convertValue(json.readTree(raw),Map.class).keySet());assertEquals(1,audits());
    }
    @Test void anonymousViewerAndCsrfCannotWriteOrReadSensitiveAccountDirectory()throws Exception{
        var anon=new Browser();assertEquals(401,anon.call("GET","accounts",null,null).statusCode());
        var v=new Browser();v.login(viewer);for(String p:List.of("accounts","accounts/audit"))assertEquals(403,v.call("GET",p,null,null).statusCode());
        assertEquals(403,v.change(other,1,"VIEWER",false).statusCode());
        var a=new Browser();a.login(admin);assertEquals(403,a.call("PUT","accounts/"+other,Map.of("role","VIEWER","enabled",false,"expectedVersion",1),null).statusCode());assertEquals(0,audits());
    }
    @Test void staleVersionAndDuplicateCreationAreSideEffectFree()throws Exception{
        var a=new Browser();a.login(admin);assertEquals(200,a.change(other,1,"VIEWER",true).statusCode());
        var stale=a.change(other,1,"ADMIN",true);assertEquals(409,stale.statusCode());assertEquals("ACCOUNT_VERSION_CONFLICT",json.readTree(stale.body()).path("code").asText());
        assertEquals(1,audits());assertEquals(2,owner().queryForObject("SELECT version FROM admin_account WHERE username=?",Integer.class,other));
        assertEquals(409,a.call("POST","accounts",Map.of("username",other,"password",password,"role","ADMIN","enabled",true),a.csrf()).statusCode());assertEquals(1,audits());
    }
    @Test void lastEnabledAdminCannotBeDisabledOrDemotedButNoopRemainsValid()throws Exception{
        owner().update("UPDATE admin_account SET enabled=false WHERE username=?",other);var a=new Browser();a.login(admin);
        for(var r:List.of(a.change(admin,1,"ADMIN",false),a.change(admin,1,"VIEWER",true))){assertEquals(409,r.statusCode());assertEquals("LAST_ADMIN",json.readTree(r.body()).path("code").asText());}
        assertEquals(200,a.change(admin,1,"ADMIN",true).statusCode());assertEquals(0,audits());assertTrue(a.session().path("authenticated").asBoolean());
    }
    @Test void twoConcurrentSelfDemotionsPreserveExactlyOneEnabledAdmin()throws Exception{
        var a=new Browser();a.login(admin);var b=new Browser();b.login(other);String ca=a.csrf(),cb=b.csrf();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var x=pool.submit(()->{gate.await();return a.call("PUT","accounts/"+admin,Map.of("role","VIEWER","enabled",true,"expectedVersion",1),ca).statusCode();});
            var y=pool.submit(()->{gate.await();return b.call("PUT","accounts/"+other,Map.of("role","VIEWER","enabled",true,"expectedVersion",1),cb).statusCode();});gate.countDown();
            var statuses=new ArrayList<>(List.of(x.get(15,TimeUnit.SECONDS),y.get(15,TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        }
        assertEquals(1,owner().queryForObject("SELECT count(*) FROM admin_account WHERE role='ADMIN' AND enabled",Integer.class));assertEquals(1,audits());
    }
    @Test void roleChangeDisableAndReenableRevokeAllOldSessionsWithoutChangingPassword()throws Exception{
        var a=new Browser();a.login(admin);var old=new Browser();old.login(other);var second=new Browser();second.login(other);
        String hash=owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?",String.class,other);
        assertEquals(200,a.change(other,1,"VIEWER",true).statusCode());assertEquals(401,old.call("GET","overview",null,null).statusCode());assertEquals(401,second.call("GET","overview",null,null).statusCode());
        assertEquals(0,owner().queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?",Integer.class,other));
        old.login(other);assertEquals(403,old.call("GET","accounts",null,null).statusCode());
        assertEquals(200,a.change(other,2,"VIEWER",false).statusCode());assertEquals(401,old.call("GET","overview",null,null).statusCode());assertEquals(401,old.call("POST","login","username="+other+"&password="+password,old.csrf()).statusCode());
        assertEquals(200,a.change(other,3,"VIEWER",true).statusCode());old.login(other);assertEquals(200,old.call("GET","overview",null,null).statusCode());
        assertEquals(hash,owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?",String.class,other));assertEquals(3,audits());
    }
    @Test void selfChangeDoesNotResurrectRequestSessionAndStaleActorIsRecheckedInTransaction()throws Exception{
        var a=new Browser();a.login(admin);assertEquals(200,a.change(admin,1,"VIEWER",true).statusCode());assertFalse(a.session().path("authenticated").asBoolean());
        assertEquals(0,owner().queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?",Integer.class,admin));
        var old=new AdminIdentity(admin,null,"ADMIN",1,true);
        var failure=assertThrows(dev.jackteng.portfolio.catalog.CatalogProblem.class,()->service.update(other,json.valueToTree(Map.of("role","VIEWER","enabled",true,"expectedVersion",1)),old));
        assertEquals(401,failure.status());assertEquals(1,audits());
    }
    @Test void auditInsertFailureRollsBackCreateUpdateAndSessionDeletion()throws Exception{
        var a=new Browser();a.login(admin);var victim=new Browser();victim.login(other);
        String before=owner().queryForObject("SELECT to_jsonb(a)::text FROM admin_account a WHERE username=?",String.class,other);
        try{
            owner().execute("REVOKE INSERT ON admin_identity_audit FROM portfolio_identity");
            var changed=a.change(other,1,"VIEWER",false);assertEquals(503,changed.statusCode());assertEquals("AUTH_UNAVAILABLE",json.readTree(changed.body()).path("code").asText());
            assertEquals(before,owner().queryForObject("SELECT to_jsonb(a)::text FROM admin_account a WHERE username=?",String.class,other));assertTrue(victim.session().path("authenticated").asBoolean());
            assertEquals(503,a.call("POST","accounts",Map.of("username",prefix+"_rollback","password",password,"role","VIEWER","enabled",true),a.csrf()).statusCode());
            assertEquals(0,owner().queryForObject("SELECT count(*) FROM admin_account WHERE username=?",Integer.class,prefix+"_rollback"));assertEquals(0,audits());
        }finally{owner().execute("GRANT INSERT ON admin_identity_audit TO portfolio_identity");}
    }
    @Test void invalidTypesUnknownFieldsImmutableNamesAndWeakPasswordsAreRejected()throws Exception{
        var a=new Browser();a.login(admin);
        for(Map<String,Object> command:List.<Map<String,Object>>of(Map.of("role","OWNER","enabled",true,"expectedVersion",1),Map.of("role","VIEWER","enabled","true","expectedVersion",1),Map.of("role","VIEWER","enabled",true,"expectedVersion","1"),Map.of("role","VIEWER","enabled",true,"expectedVersion",1,"password_hash","spoof")))
            assertEquals(400,a.call("PUT","accounts/"+other,command,a.csrf()).statusCode());
        assertEquals(400,a.call("POST","accounts",Map.of("username",prefix+"_weak","password","short","role","VIEWER","enabled",true),a.csrf()).statusCode());assertEquals(0,audits());
    }
    @Test void identityRoleHasOnlyNecessaryGrantsAndAuditCannotStoreSecretFields()throws Exception{
        assertEquals("portfolio_identity",identity.queryForObject("SELECT current_user",String.class));
        for(String query:List.of("SELECT password_hash FROM admin_account","SELECT * FROM spring_session_attributes","SELECT * FROM project_audit","UPDATE admin_account SET password_hash='bad'","DELETE FROM admin_account","UPDATE project SET title='bad'","UPDATE admin_identity_audit SET actor='spoof'","DELETE FROM admin_identity_audit","CREATE TABLE bad(id int)"))assertThrows(org.springframework.dao.DataAccessException.class,()->identity.execute(query));
        for(JdbcTemplate db:List.of(reader,auth,writer))assertThrows(org.springframework.dao.DataAccessException.class,()->db.queryForList("SELECT * FROM admin_identity_audit"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->identity.update("INSERT INTO admin_identity_audit VALUES (?,? ,?,'CREATE',NULL,'{\"password\":\"forbidden\"}'::jsonb,clock_timestamp())",UUID.randomUUID(),viewer,admin));
    }
}
