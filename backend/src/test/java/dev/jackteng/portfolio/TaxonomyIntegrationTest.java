package dev.jackteng.portfolio;

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
class TaxonomyIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired @Qualifier("writerJdbc") JdbcTemplate writer;
    @Autowired JdbcTemplate reader;
    final String suffix=UUID.randomUUID().toString().replace("-","");
    final String admin="term_admin_"+suffix, viewer="term_viewer_"+suffix, prefix="term-"+suffix.substring(0,12)+"-";
    final String password=UUID.randomUUID().toString();
    JdbcTemplate owner() { return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD"))); }
    @BeforeAll void setup() { owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN'),(?,?,'VIEWER')",admin,encoder.encode(password),viewer,encoder.encode(password)); }
    @AfterEach void removeFixtures() {
        var db=owner();
        for(String table:List.of("project_technology","project_media","project_evidence")) db.update("DELETE FROM "+table+" WHERE project_id IN(SELECT id FROM project WHERE slug LIKE ?)",prefix+"%");
        db.update("DELETE FROM project WHERE slug LIKE ?",prefix+"%");
        db.update("DELETE FROM project_audit WHERE actor IN (?,?)",admin,viewer);
        db.update("DELETE FROM taxonomy_audit WHERE actor IN (?,?)",admin,viewer);
        db.update("DELETE FROM category WHERE label LIKE ?",prefix+"%");
        db.update("DELETE FROM technology WHERE name LIKE ?",prefix+"%");
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
    final String terms="/api/admin/taxonomy/";
    JsonNode term(Client c,String kind,String name) throws Exception {
        var body=json.createObjectNode().put("label",prefix+name);if(kind.equals("categories"))body.put("sortOrder",100);
        var r=c.call("POST",terms+kind,body,true);assertEquals(201,r.statusCode(),r.body());return tree(r);
    }
    ObjectNode renamed(JsonNode row,String name) {var b=json.createObjectNode().put("label",prefix+name).put("expectedVersion",row.path("version").asLong());if(!row.path("sortOrder").isNull())b.put("sortOrder",row.path("sortOrder").asInt());return b;}
    String tp(String kind,JsonNode row){return terms+kind+"/"+row.path("id").asText();}
    JsonNode current(Client c,ObjectNode command) throws Exception {return tree(c.call("GET",path(command),null,false));}
    @Test void dynamicCategoryCreatePublishRenameProtectsOpenProjectAndRetainsDeletedAudit() throws Exception {
        var c=admin();var category=term(c,"categories","新分類");var request=change("dynamic");request.withObject("content").put("category",category.path("label").asText());request.put("published",true);create(c,request);
        assertEquals(409,c.call("DELETE",tp("categories",category),Map.of("expectedVersion",1),true).statusCode());
        var r=c.call("PUT",tp("categories",category),renamed(category,"改名分類"),true);assertEquals(200,r.statusCode(),r.body());var fresh=tree(r);assertEquals(2,fresh.path("version").asInt());assertEquals(1,fresh.path("references").asInt());
        var managed=current(c,request);assertEquals(2,managed.path("version").asInt());assertEquals(fresh.path("label"),managed.path("project").path("category"));
        assertEquals(409,c.call("PUT",path(request),update(request,json.createObjectNode().put("version",1).put("sortOrder",managed.path("sortOrder").asInt())),true).statusCode());
        var history=tree(c.call("GET",path(request)+"/audit",null,false));assertEquals(2,history.size());assertEquals(category.path("label"),history.get(0).path("before").path("project").path("category"));assertEquals(fresh.path("label"),history.get(0).path("after").path("project").path("category"));
        assertEquals(204,c.call("DELETE",path(request),Map.of("expectedVersion",2),true).statusCode());assertEquals(204,c.call("DELETE",tp("categories",fresh),Map.of("expectedVersion",2),true).statusCode());
        var audit=tree(c.call("GET",terms+"audit",null,false));assertEquals("DELETE",audit.get(0).path("action").asText());assertTrue(audit.get(0).path("after").isNull());assertEquals(3,owner().queryForObject("SELECT count(*) FROM taxonomy_audit WHERE term_id=?::uuid",Integer.class,category.path("id").asText()));
    }
    @Test void technologyRenamePropagatesToPublishedAndDraftAndDatabaseForeignKeyProtectsReferences() throws Exception {
        var c=admin();var technology=term(c,"technologies","stack");var first=change("pub");first.withObject("content").putArray("technologies").add(technology.path("label").asText());first.put("published",true);create(c,first);var second=change("draft");second.withObject("content").putArray("technologies").add(technology.path("label").asText());create(c,second);
        assertEquals(409,c.call("DELETE",tp("technologies",technology),Map.of("expectedVersion",1),true).statusCode());
        assertThrows(org.springframework.dao.DataAccessException.class,()->writer.update("DELETE FROM technology WHERE id=?::uuid",technology.path("id").asText()));
        assertEquals(200,c.call("PUT",tp("technologies",technology),renamed(technology,"renamed-stack"),true).statusCode());
        for(var request:List.of(first,second)){var value=current(c,request);assertEquals(2,value.path("version").asInt());assertEquals(prefix+"renamed-stack",value.path("project").path("technologies").get(0).asText());}
        assertEquals(4,audits());assertEquals(404,c.call("GET",path(second).replace("/admin/","/public/"),null,false).statusCode());
    }
    @Test void taxonomyAndProjectAuditFailuresRollbackEveryAffectedParentAndAllDictionaryWrites() throws Exception {
        var c=admin();var category=term(c,"categories","rollback");var a=change("rollback-a");a.withObject("content").put("category",category.path("label").asText());create(c,a);var b=change("rollback-b");b.withObject("content").put("category",category.path("label").asText());create(c,b);var beforeA=current(c,a);var beforeB=current(c,b);
        for(String table:List.of("taxonomy_audit","project_audit")){
            owner().execute("REVOKE INSERT ON "+table+" FROM portfolio_writer");try{var r=c.call("PUT",tp("categories",category),renamed(category,"not-saved"),true);assertEquals(503,r.statusCode(),r.body());}finally{owner().execute("GRANT INSERT ON "+table+" TO portfolio_writer");}
            assertEquals(beforeA,current(c,a));assertEquals(beforeB,current(c,b));assertEquals(2,audits());assertEquals(1,owner().queryForObject("SELECT version::int FROM category WHERE id=?::uuid",Integer.class,category.path("id").asText()));
        }
        owner().execute("REVOKE INSERT ON taxonomy_audit FROM portfolio_writer");try{
            assertEquals(503,c.call("POST",terms+"technologies",Map.of("label",prefix+"not-created"),true).statusCode());var t=termExisting(c,"technologies","Vue 3"); // referenced delete rejected before audit
            assertEquals(409,c.call("DELETE",tp("technologies",t),Map.of("expectedVersion",t.path("version").asLong()),true).statusCode());
        }finally{owner().execute("GRANT INSERT ON taxonomy_audit TO portfolio_writer");}
        assertEquals(0,owner().queryForObject("SELECT count(*) FROM technology WHERE name=?",Integer.class,prefix+"not-created"));
        var unused=term(c,"technologies","unused");owner().execute("REVOKE INSERT ON taxonomy_audit FROM portfolio_writer");try{assertEquals(503,c.call("DELETE",tp("technologies",unused),Map.of("expectedVersion",1),true).statusCode());}finally{owner().execute("GRANT INSERT ON taxonomy_audit TO portfolio_writer");}assertEquals(unused,termExisting(c,"technologies",unused.path("label").asText()));
    }
    JsonNode termExisting(Client c,String kind,String label) throws Exception {for(var row:tree(c.call("GET",terms+kind,null,false)))if(row.path("label").asText().equals(label))return row;throw new AssertionError("Missing term");}
    @Test void noopAndCategorySortOnlyDoNotInvalidateProjectButStaleDictionaryCommandsFail() throws Exception {
        var c=admin();var t=term(c,"categories","sort");var request=change("sort");request.withObject("content").put("category",t.path("label").asText());create(c,request);
        assertEquals(1,tree(c.call("PUT",tp("categories",t),renamed(t,"sort"),true)).path("version").asInt());assertEquals(1,audits());
        var sort=renamed(t,"sort").put("sortOrder",101);assertEquals(2,tree(c.call("PUT",tp("categories",t),sort,true)).path("version").asInt());assertEquals(1,current(c,request).path("version").asInt());
        assertEquals(409,c.call("PUT",tp("categories",t),sort,true).statusCode());assertEquals(409,c.call("DELETE",tp("categories",t),Map.of("expectedVersion",1),true).statusCode());
    }
    @Test void simultaneousTaxonomyEditorsHaveOneSuccessOneConflictAndOneUpdate() throws Exception {
        var a=admin();var b=admin();var t=term(a,"technologies","concurrent");var latch=new CountDownLatch(1);
        try(var ex=Executors.newVirtualThreadPerTaskExecutor()) {var one=ex.submit(()->{latch.await();return a.call("PUT",tp("technologies",t),renamed(t,"one"),true).statusCode();});var two=ex.submit(()->{latch.await();return b.call("PUT",tp("technologies",t),renamed(t,"two"),true).statusCode();});latch.countDown();var codes=new ArrayList<>(List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,409),codes);}
        assertEquals(2,owner().queryForObject("SELECT count(*) FROM taxonomy_audit WHERE actor=?",Integer.class,admin));
    }
    @Test void viewerAnonymousCsrfAndMinimumDatabaseGrantsPreventMutationAndAuditTampering() throws Exception {
        var c=admin();var t=term(c,"technologies","permissions");var v=new Client();v.login(viewer);assertEquals(200,v.call("GET",terms+"technologies",null,false).statusCode());assertEquals(200,v.call("GET",terms+"audit",null,false).statusCode());assertEquals(401,new Client().call("GET",terms+"categories",null,false).statusCode());
        for(String method:List.of("POST","PUT","DELETE")){String endpoint=method.equals("POST")?terms+"technologies":tp("technologies",t);assertEquals(403,v.call(method,endpoint,renamed(t,"denied"),true).statusCode());assertEquals(403,c.call(method,endpoint,renamed(t,"denied"),false).statusCode());}
        for(String sql:List.of("UPDATE taxonomy_audit SET actor='fake'","DELETE FROM taxonomy_audit","SELECT password_hash FROM admin_account","CREATE TABLE term_forbidden(id int)"))assertThrows(org.springframework.dao.DataAccessException.class,()->writer.execute(sql));
        for(String role:List.of("portfolio_reader","portfolio_auth","portfolio_identity","portfolio_credentials"))assertFalse(owner().queryForObject("SELECT has_table_privilege(?, 'taxonomy_audit','SELECT')",Boolean.class,role));
    }
    @Test void invalidStrictFieldsDuplicatesReservedNamesAndVersionOverflowNeverWrite() throws Exception {
        var c=admin();var t=term(c,"categories","validation");int count=owner().queryForObject("SELECT count(*) FROM taxonomy_audit WHERE actor=?",Integer.class,admin);
        for(Object body:List.of(Map.of("label","全部","sortOrder",102),Map.of("label",12,"sortOrder",102),Map.of("label",prefix+"x","sortOrder",1.5),Map.of("label",prefix+"x","sortOrder",-1),Map.of("label",prefix+"x","sortOrder",102,"actor","fake"),Map.of("label","x".repeat(41),"sortOrder",102)))assertEquals(400,c.call("POST",terms+"categories",body,true).statusCode());
        assertEquals(409,c.call("POST",terms+"categories",Map.of("label",t.path("label").asText(),"sortOrder",102),true).statusCode());assertEquals(409,c.call("POST",terms+"categories",Map.of("label",prefix+"new","sortOrder",100),true).statusCode());assertEquals(400,c.call("PUT",tp("categories",t),renamed(t,"bad").put("expectedVersion","1"),true).statusCode());
        assertEquals(400,c.call("GET",terms+"unknown",null,false).statusCode());assertEquals(404,c.call("PUT",terms+"categories/"+UUID.randomUUID(),renamed(t,"missing"),true).statusCode());
        owner().update("UPDATE category SET version=? WHERE id=?::uuid",Long.MAX_VALUE,t.path("id").asText());assertEquals(409,c.call("PUT",tp("categories",t),renamed(t,"overflow").put("expectedVersion",Long.MAX_VALUE),true).statusCode());assertEquals(count,owner().queryForObject("SELECT count(*) FROM taxonomy_audit WHERE actor=?",Integer.class,admin));
    }
    @Test void implicitTechnologyCreationIsAuditedAndTaxonomyFailureRollsBackProjectAndRelations() throws Exception {
        var c=admin();var request=change("implicit");request.withObject("content").withArray("technologies").add(prefix+"implicit");create(c,request);var t=termExisting(c,"technologies",prefix+"implicit");assertEquals(1,t.path("references").asInt());assertEquals(1,owner().queryForObject("SELECT count(*) FROM taxonomy_audit WHERE actor=?",Integer.class,admin));
        var bad=change("implicit-fail");bad.withObject("content").withArray("technologies").add(prefix+"implicit-fail");owner().execute("REVOKE INSERT ON taxonomy_audit FROM portfolio_writer");try{assertEquals(503,c.call("POST","/api/admin/projects",bad,true).statusCode());}finally{owner().execute("GRANT INSERT ON taxonomy_audit TO portfolio_writer");}assertEquals(404,c.call("GET",path(bad),null,false).statusCode());assertEquals(0,owner().queryForObject("SELECT count(*) FROM technology WHERE name=?",Integer.class,prefix+"implicit-fail"));
    }
    @Test void publicAndAdminBoundedPagesKeepFilteredTotalsStableOrderingAndLiteralSearch() throws Exception {
        var c=admin();var t=term(c,"categories","page-category");for(int i=0;i<5;i++){var req=change("page-"+i);req.withObject("content").put("category",t.path("label").asText()).put("title",prefix+"literal%_ "+i);req.put("published",i<4);create(c,req);}
        String query="?category="+URLEncoder.encode(t.path("label").asText(),java.nio.charset.StandardCharsets.UTF_8)+"&pageSize=2";
        var one=tree(c.call("GET","/api/public/projects"+query+"&page=1",null,false));var two=tree(c.call("GET","/api/public/projects"+query+"&page=2",null,false));var beyond=tree(c.call("GET","/api/public/projects"+query+"&page=3",null,false));assertEquals(4,one.path("total").asInt());assertEquals(2,one.path("totalPages").asInt());assertEquals(9,one.path("catalogTotal").asInt());assertEquals(2,one.path("items").size());assertEquals(prefix+"page-2",two.path("items").get(0).path("slug").asText());assertEquals(0,beyond.path("items").size());assertEquals(4,beyond.path("total").asInt());
        var adminPage=tree(c.call("GET","/api/admin/projects"+query+"&published=false",null,false));assertEquals(1,adminPage.path("total").asInt());assertEquals(prefix+"page-4",adminPage.path("items").get(0).path("slug").asText());
        var literal=tree(c.call("GET","/api/public/projects?q="+URLEncoder.encode(prefix+"literal%_",java.nio.charset.StandardCharsets.UTF_8),null,false));assertEquals(4,literal.path("total").asInt());
        assertEquals(one,tree(c.call("GET","/api/public/projects"+query+"&page=1",null,false)));assertEquals(0,tree(c.call("GET","/api/admin/projects?q=none"+suffix,null,false)).path("total").asInt());
    }
    @Test void invalidPaginationBoundsRejectAndUnusedRegisteredCategoryIsEmptyRatherThanUnknown() throws Exception {
        var c=admin();var t=term(c,"categories","unused-filter");String query="?category="+URLEncoder.encode(t.path("label").asText(),java.nio.charset.StandardCharsets.UTF_8);assertEquals(0,tree(c.call("GET","/api/public/projects"+query,null,false)).path("total").asInt());
        for(String root:List.of("/api/public/projects","/api/admin/projects"))for(String bad:List.of("page=0","page=-1","page=1.5","page=100001","page=0001","pageSize=0","pageSize=51","pageSize=100","pageSize=x"))assertEquals(400,c.call("GET",root+"?"+bad,null,false).statusCode(),root+bad);
        assertEquals(400,c.call("GET","/api/admin/projects?published=maybe",null,false).statusCode());assertEquals(400,c.call("GET","/api/public/projects?category=unknown",null,false).statusCode());
    }
    @Test void nextPublicProjectCanNavigatePastPageBoundaryWrapsAndNeverExposesDraft() throws Exception {
        var c=admin();var request=change("last");request.put("published",true);create(c,request);var draft=change("later-draft");create(c,draft);
        assertEquals(prefix+"last",tree(c.call("GET","/api/public/projects/developer-portfolio/next",null,false)).path("next").path("slug").asText());assertEquals("fadaiting-camp",tree(c.call("GET",path(request).replace("/admin/","/public/")+"/next",null,false)).path("next").path("slug").asText());assertEquals(404,c.call("GET",path(draft).replace("/admin/","/public/")+"/next",null,false).statusCode());
    }
    @Test void dictionaryCapacityAndBroadRenameAreBoundedWithoutPartialChange() throws Exception {
        var c=admin();
        owner().update("INSERT INTO technology(id,name) SELECT gen_random_uuid(),?||i FROM generate_series(1,468) i",prefix+"capacity-");
        assertEquals(409,c.call("POST",terms+"technologies",Map.of("label",prefix+"too-many"),true).statusCode());
        var request=change("capacity");request.withObject("content").withArray("technologies").add(prefix+"too-many");
        assertEquals(409,c.call("POST","/api/admin/projects",request,true).statusCode());assertEquals(404,c.call("GET",path(request),null,false).statusCode());assertEquals(0,audits());
        var t=term(c,"categories","broad");
        owner().update("INSERT INTO project(id,slug,category_id,title,subtitle,status,status_note,summary,description,year,role,duration,featured,published,theme,technology_label,sort_order,features,challenges,architecture) SELECT gen_random_uuid(),?||i,?::uuid,'Own bound fixture','','In Progress','','Synthetic','Synthetic','2026','Developer','Test',false,false,'forest','Test',1000+i,'[]'::jsonb,'[]'::jsonb,'[]'::jsonb FROM generate_series(1,101) i",prefix+"broad-",t.path("id").asText());
        var response=c.call("PUT",tp("categories",t),renamed(t,"too-broad"),true);assertEquals(409,response.statusCode());assertEquals("TERM_RENAME_LIMIT",tree(response).path("code").asText());assertEquals(0,audits());assertEquals(101,termExisting(c,"categories",t.path("label").asText()).path("references").asInt());
        assertEquals(101,owner().queryForObject("SELECT count(*) FROM project WHERE slug LIKE ? AND version=1",Integer.class,prefix+"broad-%"));
    }
}
