package dev.elpsy.portfolio;

import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"portfolio.test-mode=true","portfolio.auth.cookie-secure=false", "portfolio.media.scheduled-cleanup=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminProjectIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired @Qualifier("writerJdbc") JdbcTemplate writer;
    @Autowired JdbcTemplate reader;
    final String suffix=UUID.randomUUID().toString().replace("-","");
    final String admin="crud_admin_"+suffix, viewer="crud_viewer_"+suffix, prefix="crud-"+suffix+"-";
    final String password=UUID.randomUUID().toString();
    JdbcTemplate owner() { return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD"))); }
    @BeforeAll void setup() { owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN'),(?,?,'VIEWER')",admin,encoder.encode(password),viewer,encoder.encode(password)); }
    @AfterEach void removeFixtures() {
        var db=owner();
        for(String table:List.of("project_technology","project_media","project_evidence")) db.update("DELETE FROM "+table+" WHERE project_id IN(SELECT id FROM project WHERE slug LIKE ?)",prefix+"%");
        db.update("DELETE FROM project WHERE slug LIKE ?",prefix+"%");
        db.update("DELETE FROM project_audit WHERE actor IN (?,?)",admin,viewer);
        db.update("DELETE FROM taxonomy_audit WHERE actor IN (?,?)",admin,viewer);
        db.update("DELETE FROM technology WHERE name=?","Own technology "+suffix);
    }
    @AfterAll void cleanup() { owner().update("DELETE FROM spring_session WHERE principal_name IN (?,?)",admin,viewer); owner().update("DELETE FROM admin_account WHERE username IN (?,?)",admin,viewer); }
    final class Client {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf;
        HttpResponse<String> call(String method,String path,Object body,boolean token) throws Exception {
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
            if(token && csrf!=null) req.header("X-CSRF-TOKEN",csrf);
            String text=body==null?"":json.writeValueAsString(body);
            if(body!=null) req.header("Content-Type","application/json");
            return http.send(req.method(method,HttpRequest.BodyPublishers.ofString(text)).build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(String username) throws Exception {
            csrf=tree(call("GET","/api/admin/session",null,false)).path("csrf").path("token").asText();
            var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/login")).header("X-CSRF-TOKEN",csrf)
                    .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username="+username+"&password="+password)).build();
            assertEquals(204,http.send(req,HttpResponse.BodyHandlers.ofString()).statusCode());
            csrf=tree(call("GET","/api/admin/session",null,false)).path("csrf").path("token").asText();
        }
    }
    JsonNode tree(HttpResponse<String> r) throws Exception { return json.readTree(r.body()); }
    Client admin() throws Exception { var c=new Client();c.login(admin);return c; }
    ObjectNode change(String name) {
        var c=json.createObjectNode();
        var content=c.putObject("content");
        content.put("slug",prefix+name).put("title","Synthetic project").put("subtitle","Temporary fixture").put("category","全端應用").put("status","In Progress")
                .put("statusNote","Test only").put("summary","Own synthetic catalog").put("description","Isolated HTTP verification").put("year","2026").put("role","Developer").put("duration","Fixture")
                .put("featured",false).put("theme","forest").put("technologyLabel","Verified stack");
        content.putArray("technologies").add("Vue 3"); content.putArray("features").add("Evidence");content.putArray("challenges");content.putArray("architecture");
        c.put("published",false);return c;
    }
    ObjectNode update(ObjectNode request,JsonNode result) { var c=request.deepCopy();c.put("expectedVersion",result.path("version").asLong());c.put("sortOrder",result.path("sortOrder").asInt());return c; }
    JsonNode create(Client c,ObjectNode request) throws Exception { var r=c.call("POST","/api/admin/projects",request,true);assertEquals(201,r.statusCode(),r.body());return tree(r); }
    String path(ObjectNode c) { return "/api/admin/projects/"+c.path("content").path("slug").asText(); }
    int audits() { return owner().queryForObject("SELECT count(*) FROM project_audit WHERE actor=?",Integer.class,admin); }
    @Test void fullLifecycleDraftPublishUpdateUnpublishDeleteAndAuditRetained() throws Exception {
        var c=admin();var request=change("lifecycle");var first=create(c,request);String path=path(request);
        assertEquals(404,c.call("GET",path.replace("/admin/","/public/"),null,false).statusCode());
        assertEquals(200,c.call("GET",path,null,false).statusCode());assertFalse(first.path("published").asBoolean());
        var edit=update(request,first);edit.put("published",true);edit.withObject("content").put("title","Published synthetic project");
        var published=c.call("PUT",path,edit,true);assertEquals(200,published.statusCode());var second=tree(published);assertEquals(2,second.path("version").asLong());
        assertEquals("Published synthetic project",tree(c.call("GET",path.replace("/admin/","/public/"),null,false)).path("title").asText());
        var hide=update(edit,second);hide.put("published",false);var third=tree(c.call("PUT",path,hide,true));
        assertEquals(404,c.call("GET",path.replace("/admin/","/public/"),null,false).statusCode());
        assertEquals(204,c.call("DELETE",path,Map.of("expectedVersion",third.path("version").asLong()),true).statusCode());
        assertEquals(404,c.call("GET",path,null,false).statusCode());
        var audit=tree(c.call("GET",path+"/audit",null,false));assertEquals(4,audit.size());assertEquals("DELETE",audit.get(0).path("action").asText());
        assertEquals(3,audit.get(0).path("before").path("version").asInt());assertTrue(audit.get(0).path("after").isNull());
        assertEquals(admin,audit.get(3).path("actor").asText());assertTrue(audit.get(3).path("before").isNull());
        assertEquals(409,c.call("POST","/api/admin/projects",request,true).statusCode()); // No delete/recreate ABA using stale versions.
    }
    @Test void staleUpdateAndDeleteReturn409WithNoChangesOrAudit() throws Exception {
        var c=admin();var request=change("stale");var first=create(c,request);var edit=update(request,first);
        edit.withObject("content").put("title","New title");assertEquals(200,c.call("PUT",path(request),edit,true).statusCode());
        assertEquals(409,c.call("PUT",path(request),edit,true).statusCode());assertEquals(409,c.call("DELETE",path(request),Map.of("expectedVersion",1),true).statusCode());
        assertEquals(2,audits());assertEquals("New title",tree(c.call("GET",path(request),null,false)).path("project").path("title").asText());
    }
    @Test void simultaneousEditorsHaveOneSuccessOneConflictAndOnlyOneUpdateAudit() throws Exception {
        var first=admin();var second=admin();var request=change("concurrent");var created=create(first,request);
        var a=update(request,created);var b=update(request,created);a.withObject("content").put("title","Editor A");b.withObject("content").put("title","Editor B");
        var latch=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var one=executor.submit(()->{latch.await();return first.call("PUT",path(request),a,true).statusCode();});
            var two=executor.submit(()->{latch.await();return second.call("PUT",path(request),b,true).statusCode();});latch.countDown();
            var codes=new ArrayList<>(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);
        }
        assertEquals(2,audits());assertEquals(2,tree(first.call("GET",path(request),null,false)).path("version").asInt());
    }
    @Test void viewerReadsDraftAndAuditButAllWritesAre403AndAnonymousCannotRead() throws Exception {
        var c=admin();var request=change("permissions");var created=create(c,request);var v=new Client();v.login(viewer);
        assertEquals(200,v.call("GET","/api/admin/projects",null,false).statusCode());assertEquals(200,v.call("GET",path(request),null,false).statusCode());assertEquals(200,v.call("GET",path(request)+"/audit",null,false).statusCode());
        for(String method:List.of("POST","PUT","DELETE")) assertEquals(403,v.call(method,method.equals("POST")?"/api/admin/projects":path(request),update(request,created),true).statusCode());
        assertEquals(401,new Client().call("GET",path(request),null,false).statusCode());assertEquals(1,audits());
    }
    @Test void missingCsrfBlocksAllWritesWithoutChangingRows() throws Exception {
        var c=admin();var request=change("csrf");var first=create(c,request);
        for(String method:List.of("POST","PUT","DELETE")) assertEquals(403,c.call(method,method.equals("POST")?"/api/admin/projects":path(request),update(request,first),false).statusCode());
        assertEquals(1,audits());assertEquals(1,tree(c.call("GET",path(request),null,false)).path("version").asInt());
    }
    @Test void duplicateSlugAndOrderReturn409AndImmutableSlugOrMissingVersion400() throws Exception {
        var c=admin();var request=change("duplicate");var first=create(c,request);
        assertEquals(409,c.call("POST","/api/admin/projects",request,true).statusCode());
        var edit=update(request,first);edit.put("sortOrder",0);assertEquals(409,c.call("PUT",path(request),edit,true).statusCode());
        edit=update(request,first);edit.withObject("content").put("slug",prefix+"other");assertEquals(400,c.call("PUT",path(request),edit,true).statusCode());
        assertEquals(400,c.call("PUT",path(request),request,true).statusCode());assertEquals(400,c.call("DELETE",path(request),Map.of(),true).statusCode());assertEquals(1,audits());
    }
    @Test void automaticOrderExhaustionReturnsConflictRatherThanOverflowOrPartialWrite() throws Exception {
        var c=admin();var last=change("last-order");last.put("sortOrder",Integer.MAX_VALUE);create(c,last);
        var request=change("no-room");var result=c.call("POST","/api/admin/projects",request,true);
        assertEquals(409,result.statusCode());assertEquals("ORDER_CONFLICT",tree(result).path("code").asText());
        assertEquals(404,c.call("GET",path(request),null,false).statusCode());assertEquals(1,audits());
    }
    @Test void badFieldsLinksDuplicateTagsAndUnknownPropertiesAreRejectedBeforeWrite() throws Exception {
        var c=admin();var original=change("invalid");
        for(String field:List.of("title","category","status","theme","githubUrl","evidenceCheckedAt")) {
            var request=original.deepCopy();request.withObject("content").put(field,field.equals("title")?" ":"invalid");
            assertEquals(400,c.call("POST","/api/admin/projects",request,true).statusCode(),field);
        }
        var tags=original.deepCopy();tags.withObject("content").withArray("technologies").add("Vue 3");assertEquals(400,c.call("POST","/api/admin/projects",tags,true).statusCode());
        var unknown=original.deepCopy();unknown.put("actor","spoofed");assertEquals(400,c.call("POST","/api/admin/projects",unknown,true).statusCode());
        assertEquals(0,audits());assertEquals(5,reader.queryForObject("SELECT count(*) FROM project",Integer.class));
    }
    @Test void auditInsertFailureRollsBackProjectRelationsAndNewTechnology() throws Exception {
        var c=admin();var request=change("rollback");request.withObject("content").withArray("technologies").add("Own technology "+suffix);
        owner().execute("REVOKE INSERT ON project_audit FROM portfolio_writer");
        try { var r=c.call("POST","/api/admin/projects",request,true);assertEquals(503,r.statusCode());assertEquals("CATALOG_UNAVAILABLE",tree(r).path("code").asText()); }
        finally { owner().execute("GRANT INSERT ON project_audit TO portfolio_writer"); }
        assertEquals(404,c.call("GET",path(request),null,false).statusCode());assertEquals(0,audits());assertEquals(0,owner().queryForObject("SELECT count(*) FROM technology WHERE name=?",Integer.class,"Own technology "+suffix));
    }
    @Test void auditFailureAlsoRollsBackUpdateAndDeleteIncludingChildren() throws Exception {
        var c=admin();var request=change("rollback-update");var first=create(c,request);var edit=update(request,first);edit.withObject("content").put("title","Do not save");
        owner().execute("REVOKE INSERT ON project_audit FROM portfolio_writer");
        try { assertEquals(503,c.call("PUT",path(request),edit,true).statusCode());assertEquals(503,c.call("DELETE",path(request),Map.of("expectedVersion",1),true).statusCode()); }
        finally { owner().execute("GRANT INSERT ON project_audit TO portfolio_writer"); }
        var after=tree(c.call("GET",path(request),null,false));assertEquals(first,after);assertEquals(1,audits());
    }
    @Test void storedOrderAndTechEvidenceAreVisibleOnlyAfterPublication() throws Exception {
        var c=admin();var request=change("relations");request.put("published",true).put("sortOrder",40);
        request.withObject("content").put("evidenceCheckedAt","2026-10-12").putArray("evidenceLinks").addObject().put("label","Own evidence").put("url","https://example.com/evidence");
        var first=create(c,request);assertEquals(40,first.path("sortOrder").asInt());
        var detail=tree(c.call("GET",path(request).replace("/admin/","/public/"),null,false));assertEquals(1,detail.path("evidenceLinks").size());assertEquals("2026-10-12",detail.path("evidenceCheckedAt").asText());
        var listing=tree(c.call("GET","/api/public/projects",null,false)).path("items");assertEquals(prefix+"relations",listing.get(listing.size()-1).path("slug").asText());
    }
    @Test void existingImagesAndLocalDemoArePreservedOnContentUpdate() throws Exception {
        var c=admin();var request=change("media");var first=create(c,request);String slug=request.path("content").path("slug").asText();
        owner().update("INSERT INTO project_media(id,project_id,kind,position,title,description,src,alt) SELECT ?,id,'SCREENSHOT',0,'Own image','Keep metadata','/projects/test.png','Test' FROM project WHERE slug=?",UUID.randomUUID(),slug);
        owner().update("UPDATE project SET local_demo_url='http://localhost:1234' WHERE slug=?",slug);
        var before=tree(c.call("GET",path(request),null,false));var result=tree(c.call("PUT",path(request),update(request,first),true));
        assertEquals(before.path("project").path("screenshots"),result.path("project").path("screenshots"));assertEquals(before.path("project").path("localDemoUrl"),result.path("project").path("localDemoUrl"));
    }
    @Test void writerPermissionsAreNarrowAndAuditIsAppendOnlyAndPrivate() {
        assertEquals("portfolio_writer",writer.queryForObject("SELECT current_user",String.class));
        for(String sql:List.of("SELECT password_hash FROM admin_account","UPDATE admin_account SET enabled=false","UPDATE project_audit SET actor='forged'","DELETE FROM project_audit","CREATE TABLE forbidden(id int)","SELECT * FROM spring_session"))
            assertThrows(org.springframework.dao.DataAccessException.class,()->writer.execute(sql));
        assertThrows(org.springframework.dao.DataAccessException.class,()->reader.execute("SELECT * FROM project_audit"));
    }
}
