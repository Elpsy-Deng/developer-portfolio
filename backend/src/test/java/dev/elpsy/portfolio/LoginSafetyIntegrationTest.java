package dev.elpsy.portfolio;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import dev.elpsy.portfolio.auth.LoginLimits;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL counters and persisted Spring Session attributes, without clock/security mocks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "portfolio.test-mode=true", "portfolio.auth.cookie-secure=false", "portfolio.media.scheduled-cleanup=false",
        "portfolio.auth.login-account-limit=3", "portfolio.auth.login-peer-limit=5",
        "portfolio.auth.login-global-limit=8", "portfolio.auth.login-max-buckets=12"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoginSafetyIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired LoginLimits limits;
    @Autowired @Qualifier("authJdbc") JdbcTemplate auth;
    @Autowired JdbcTemplate reader;
    @Autowired @Qualifier("writerJdbc") JdbcTemplate writer;
    final String username = "safe_" + UUID.randomUUID().toString().replace("-", "");
    final String password = UUID.randomUUID().toString();
    static final String ISSUED = "portfolio.auth.login-time";
    JdbcTemplate owner() { return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),
            System.getenv("PORTFOLIO_MIGRATION_USER"), System.getenv("PORTFOLIO_MIGRATION_PASSWORD"))); }
    @BeforeAll void provision() { owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN')", username, encoder.encode(password)); }
    @BeforeEach void reset() { owner().update("DELETE FROM admin_login_bucket"); }
    @AfterAll void cleanup() {
        owner().update("DELETE FROM spring_session WHERE principal_name=?", username);
        owner().update("DELETE FROM admin_account WHERE username=?", username);
        owner().update("DELETE FROM admin_login_bucket");
    }
    class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> call(String method, String path, String body, String token, String claimedPeer) throws Exception {
            var r = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin/" + path)).timeout(Duration.ofSeconds(10));
            if (body != null) r.header("Content-Type", "application/x-www-form-urlencoded");
            if (token != null) r.header("X-CSRF-TOKEN", token);
            if (claimedPeer != null) r.header("X-Forwarded-For", claimedPeer).header("Forwarded", "for=" + claimedPeer).header("X-Real-IP", claimedPeer);
            return http.send(r.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode session() throws Exception { var r = call("GET", "session", null, null, null); assertEquals(200,r.statusCode()); return json.readTree(r.body()); }
        String csrf() throws Exception { return session().path("csrf").path("token").asText(); }
        HttpResponse<String> attempt(String name, String secret, String peer) throws Exception {
            return call("POST", "login", "username=" + name + "&password=" + secret, csrf(), peer);
        }
        void login() throws Exception { assertEquals(204, attempt(username,password,null).statusCode()); }
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("PORTFOLIO_SESSION")).findFirst().orElseThrow().getValue(); }
    }
    void issued(long millis) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var object = new ObjectOutputStream(out)) { object.writeObject(millis); }
        assertEquals(1,owner().update("""
                UPDATE spring_session_attributes a SET attribute_bytes=? FROM spring_session s
                WHERE a.session_primary_id=s.primary_id AND s.principal_name=? AND a.attribute_name=?
                """,out.toByteArray(),username,ISSUED));
    }
    long issued() throws Exception {
        var raw = owner().queryForObject("""
                SELECT a.attribute_bytes FROM spring_session_attributes a JOIN spring_session s ON s.primary_id=a.session_primary_id
                WHERE s.principal_name=? AND a.attribute_name=?
                """,byte[].class,username,ISSUED);
        try (var object = new ObjectInputStream(new ByteArrayInputStream(raw))) { return (Long)object.readObject(); }
    }
    int loginBuckets() { return owner().queryForObject("SELECT count(*) FROM admin_login_bucket WHERE bucket_key NOT IN (?,?)",Integer.class,hash("session:global"),hash("session:peer:127.0.0.1")); }
    int loginMaximum() { return owner().queryForObject("SELECT coalesce(max(attempts),0) FROM admin_login_bucket WHERE bucket_key NOT IN (?,?)",Integer.class,hash("session:global"),hash("session:peer:127.0.0.1")); }
    String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
    void clearSessions() { owner().update("DELETE FROM spring_session WHERE principal_name=?", username); }
    @Test void accountLimitsAlsoBlockCorrectCredentialsAndDoNotExposeIdentity() throws Exception {
        clearSessions(); var b = new Browser();
        for (int i=0;i<3;i++) assertEquals(401,b.attempt(username,"wrong",null).statusCode());
        var denied = b.attempt(username,password,null);
        assertEquals(429,denied.statusCode()); assertEquals("LOGIN_RATE_LIMITED",json.readTree(denied.body()).path("code").asText());
        assertTrue(Integer.parseInt(denied.headers().firstValue("Retry-After").orElseThrow())>0);
        assertEquals("no-store",denied.headers().firstValue("Cache-Control").orElseThrow());
        assertFalse(denied.body().contains(username)); assertFalse(denied.body().contains(password));
        assertFalse(b.session().path("authenticated").asBoolean());
        assertEquals(3,loginMaximum());
        owner().queryForList("SELECT bucket_key FROM admin_login_bucket",String.class).forEach(k->assertTrue(k.matches("[0-9a-f]{64}")));
    }
    @Test void realPeerLimitCannotBeBypassedWithCookiesUsernamesOrForwardedHeaders() throws Exception {
        for (int i=0;i<5;i++) assertEquals(401,new Browser().attempt("missing_"+i,"wrong","203.0.113."+(i+1)).statusCode());
        assertEquals(429,new Browser().attempt("another_missing","wrong","198.51.100.12").statusCode());
        assertEquals(5,loginMaximum());
    }
    @Test void usernameCaseAndWhitespaceCannotEscapeAccountBucket() throws Exception {
        var b = new Browser();
        for (String name:List.of("unknown", "UNKNOWN", "%20unknown%20")) assertEquals(401,b.attempt(name,"wrong",null).statusCode());
        assertEquals(429,b.attempt("unknown","wrong",null).statusCode());
    }
    @Test void globalBoundAndCapacityStayBoundedAndExpiredWindowsRecover() {
        // Direct admission service uses real auth role/transaction for distinct synthetic socket peers.
        for (int i=0;i<5;i++) assertEquals(0,limits.admit("192.0.2."+i,"user"+i));
        assertTrue(limits.admit("192.0.2.99","newuser")>0);
        assertEquals(11,owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class));
        owner().update("DELETE FROM admin_login_bucket");
        for(int i=0;i<8;i++) assertEquals(0,limits.admit("192.0.2."+(i%2),"user"+(i%4)));
        assertTrue(limits.admit("192.0.2.33","newuser")>0);
        owner().update("UPDATE admin_login_bucket SET window_start=window_start-301");
        assertEquals(0,limits.admit("192.0.2.33","newuser"));
        assertEquals(3,owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class));
    }
    @Test void simultaneousLastSlotNeverExceedsPersistedLimit() throws Exception {
        var a = new Browser(); var b = new Browser();
        assertEquals(401,a.attempt(username,"wrong",null).statusCode()); assertEquals(401,b.attempt(username,"wrong",null).statusCode());
        String ta=a.csrf(),tb=b.csrf(); var start = new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var one=workers.submit(()->{start.await();return a.call("POST","login","username="+username+"&password=wrong",ta,null).statusCode();});
            var two=workers.submit(()->{start.await();return b.call("POST","login","username="+username+"&password=wrong",tb,null).statusCode();});
            start.countDown(); var statuses=new ArrayList<>(List.of(one.get(12,TimeUnit.SECONDS),two.get(12,TimeUnit.SECONDS))); Collections.sort(statuses);
            assertEquals(List.of(401,429),statuses);
        }
        assertEquals(3,loginMaximum());
    }
    @Test void csrfFailureConsumesNoAdmissionAndMalformedCredentialsAreGeneric() throws Exception {
        var b=new Browser(); b.session();
        assertEquals(403,b.call("POST","login","username="+username+"&password=wrong",null,null).statusCode());
        assertEquals(0,loginBuckets());
        var bad=b.attempt("x".repeat(65),"wrong",null);
        assertEquals(401,bad.statusCode()); assertEquals("INVALID_CREDENTIALS",json.readTree(bad.body()).path("code").asText());
    }
    @Test void absoluteExpiryIgnoresActivityRevokesOldCookieAndAllowsFreshAuthentication() throws Exception {
        clearSessions(); var b=new Browser(); b.login(); String cookie=b.cookie(),token=b.csrf();
        long before=issued(); assertTrue(before>0);
        assertEquals(200,b.call("GET","overview",null,null,null).statusCode()); assertEquals(before,issued());
        issued(System.currentTimeMillis()-28801000);
        owner().update("UPDATE spring_session SET last_access_time=?,expiry_time=? WHERE principal_name=?",System.currentTimeMillis(),System.currentTimeMillis()+1800000,username);
        assertEquals(401,b.call("POST","projects","{}",token,null).statusCode());
        assertEquals(0,owner().queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?",Integer.class,username));
        var replay=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/overview")).header("Cookie","PORTFOLIO_SESSION="+cookie).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,replay.statusCode()); assertFalse(b.session().path("authenticated").asBoolean()); b.login();
        assertTrue(b.session().path("authenticated").asBoolean()); assertTrue(issued()>before);
    }
    @Test void missingLegacyAndFutureTimestampFailClosedWithoutRewritingLoginTime() throws Exception {
        for(boolean future:List.of(false,true)) {
            clearSessions(); var b=new Browser(); b.login();
            if(future) issued(System.currentTimeMillis()+60000);
            else owner().update("DELETE FROM spring_session_attributes WHERE attribute_name=? AND session_primary_id IN (SELECT primary_id FROM spring_session WHERE principal_name=?)",ISSUED,username);
            assertFalse(b.session().path("authenticated").asBoolean());
            assertEquals(401,b.call("GET","overview",null,null,null).statusCode());
        }
    }
    @Test void onlyExplicitReauthenticationRenewsAbsoluteLoginTime() throws Exception {
        clearSessions(); var b=new Browser(); b.login(); issued(System.currentTimeMillis()-60000); long old=issued();
        assertTrue(b.session().path("authenticated").asBoolean()); assertEquals(old,issued()); b.login(); assertTrue(issued()>old);
        var s=json.readTree(b.call("GET","security",null,null,null).body());
        assertEquals(28800,s.path("absoluteTimeoutSeconds").asInt()); assertEquals(3,s.path("loginAccountLimit").asInt());
    }
    @Test void limiterFailureIs503AndRolesCannotReadCountersOrEscalateAccounts() throws Exception {
        clearSessions(); var b=new Browser(); String token=b.csrf();
        assertThrows(org.springframework.dao.DataAccessException.class,()->reader.queryForList("SELECT * FROM admin_login_bucket"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->writer.queryForList("SELECT * FROM admin_login_bucket"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->auth.execute("UPDATE admin_account SET role='ADMIN'"));
        try {
            owner().execute("REVOKE SELECT ON admin_login_bucket FROM portfolio_auth");
            var failed=b.call("POST","login","username="+username+"&password="+password,token,null);
            assertEquals(503,failed.statusCode()); assertEquals("AUTH_UNAVAILABLE",json.readTree(failed.body()).path("code").asText());
            assertEquals(0,owner().queryForObject("SELECT count(*) FROM spring_session WHERE principal_name=?",Integer.class,username));
        } finally { owner().execute("GRANT SELECT ON admin_login_bucket TO portfolio_auth"); }
    }
}
