package dev.elpsy.portfolio;

import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import dev.elpsy.portfolio.auth.LoginLimits;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "portfolio.test-mode=true","portfolio.auth.cookie-secure=false","portfolio.media.scheduled-cleanup=false",
    "portfolio.auth.session-peer-limit=3","portfolio.auth.session-global-limit=5","portfolio.auth.login-max-buckets=8"})
class AnonymousSessionIntegrationTest {
    @LocalServerPort int port;
    @Autowired LoginLimits limits;
    JdbcTemplate owner(){return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD")));}
    Set<String> ids;
    @BeforeEach void reset(){owner().update("DELETE FROM admin_login_bucket");ids=new HashSet<>(owner().queryForList("SELECT primary_id FROM spring_session",String.class));}
    @AfterEach void cleanup(){owner().queryForList("SELECT primary_id FROM spring_session",String.class).stream().filter(id->!ids.contains(id)).forEach(id->owner().update("DELETE FROM spring_session WHERE primary_id=?",id));owner().update("DELETE FROM admin_login_bucket");}
    HttpResponse<String> get(HttpClient client,String path,String peer)throws Exception{
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("X-Forwarded-For",peer).header("X-Portfolio-Peer",peer).build(),HttpResponse.BodyHandlers.ofString());
    }
    int sessions(){return owner().queryForObject("SELECT count(*) FROM spring_session",Integer.class);}
    @Test void freshSessionsBoundedBeforeCsrfAndFakeCookiesOrClaimsCannotBypass()throws Exception{
        int before=sessions();var h=HttpClient.newHttpClient();
        for(int i=0;i<3;i++)assertEquals(200,get(h,"/api/admin/session","203.0.113."+i).statusCode());
        var denied=get(h,"/api/admin/session","198.51.100.1");assertEquals(429,denied.statusCode());assertTrue(denied.body().contains("SESSION_RATE_LIMITED"));
        assertTrue(denied.headers().firstValue("Retry-After").isPresent());assertTrue(denied.headers().allValues("Set-Cookie").isEmpty());assertEquals(before+3,sessions());
        var fake=h.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/session")).header("Cookie","PORTFOLIO_SESSION=fake").build(),HttpResponse.BodyHandlers.ofString());assertEquals(429,fake.statusCode());assertEquals(before+3,sessions());
    }
    @Test void existingSessionSurvivesAdmissionExhaustionAndPublicRemainsStateless()throws Exception{
        var jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var h=HttpClient.newBuilder().cookieHandler(jar).build();
        assertEquals(200,get(h,"/api/admin/session","203.0.113.1").statusCode());
        for(int i=0;i<2;i++)assertEquals(200,get(HttpClient.newHttpClient(),"/api/admin/session","203.0.113.2").statusCode());
        assertEquals(429,get(HttpClient.newHttpClient(),"/api/admin/session","203.0.113.3").statusCode());
        assertEquals(200,get(h,"/api/admin/session","203.0.113.4").statusCode());int count=sessions();
        var r=get(HttpClient.newHttpClient(),"/api/public/projects","203.0.113.5");assertEquals(200,r.statusCode());assertTrue(r.headers().allValues("Set-Cookie").isEmpty());assertEquals(count,sessions());
    }
    @Test void globalCapacityAndDatabaseClockWindowsStayBounded(){
        for(int i=0;i<5;i++)assertEquals(0,limits.admitAnonymous("192.0.2."+i));assertTrue(limits.admitAnonymous("192.0.2.9")>0);assertEquals(6,owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class));
        owner().update("UPDATE admin_login_bucket SET window_start=window_start-301");assertEquals(0,limits.admitAnonymous("192.0.2.9"));assertEquals(2,owner().queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class));
    }
    @Test void simultaneousLastSlotCannotPersistExtraSession()throws Exception{
        var h=HttpClient.newHttpClient();assertEquals(200,get(h,"/api/admin/session","a").statusCode());assertEquals(200,get(h,"/api/admin/session","b").statusCode());
        int before=sessions();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return get(h,"/api/admin/session","c").statusCode();});var b=pool.submit(()->{start.await();return get(h,"/api/admin/session","d").statusCode();});start.countDown();
            var codes=new ArrayList<>(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,429),codes);
        }assertEquals(before+1,sessions());
    }
    @Test void shortAdmissionContentionDoesNotRejectThreeAvailableSessionSlots()throws Exception{
        int before=sessions();var start=new CountDownLatch(1);var h=HttpClient.newHttpClient();
        try(var lock=owner().getDataSource().getConnection();var statement=lock.createStatement();var pool=Executors.newFixedThreadPool(3)){
            lock.setAutoCommit(false);statement.execute("SELECT pg_advisory_xact_lock(74041306)");
            var requests=new ArrayList<Future<Integer>>();
            for(int i=0;i<3;i++)requests.add(pool.submit(()->{start.await();return get(h,"/api/admin/session","synthetic").statusCode();}));
            start.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(owner().queryForObject("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND objid=74041306 AND NOT granted",Integer.class)<3){
                assertTrue(System.nanoTime()<end,"New Sessions should queue behind the short own admission lock");Thread.sleep(10);
            }
            lock.commit();
            for(var request:requests)assertEquals(200,request.get(8,TimeUnit.SECONDS));
        }assertEquals(before+3,sessions());
    }
    @Test void databaseAdmissionFailureDoesNotCreateSession()throws Exception{
        int before=sessions();try{owner().execute("REVOKE SELECT ON admin_login_bucket FROM portfolio_auth");var r=get(HttpClient.newHttpClient(),"/api/admin/session","a");assertEquals(503,r.statusCode());assertFalse(r.body().contains("SELECT"));assertEquals(before,sessions());}
        finally{owner().execute("GRANT SELECT ON admin_login_bucket TO portfolio_auth");}
    }
}
