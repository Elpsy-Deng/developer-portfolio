package dev.elpsy.portfolio;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"portfolio.test-mode=true","portfolio.auth.cookie-secure=false","portfolio.media.scheduled-cleanup=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    final String token=UUID.randomUUID().toString().replace("-","");
    final String admin="media_admin_"+token,viewer="media_viewer_"+token,password=UUID.randomUUID().toString();
    final Path root=Path.of(System.getenv("PORTFOLIO_MEDIA_ROOT"));
    final Set<String> projects=new LinkedHashSet<>();
    Set<UUID> initial; Client actor; String slug; long version;
    JdbcTemplate owner() { return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD"))); }
    @BeforeAll void accounts() { owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN'),(?,?,'VIEWER')",admin,encoder.encode(password),viewer,encoder.encode(password)); }
    Set<UUID> assets() { return new HashSet<>(owner().query("SELECT id FROM image_asset",(r,n)->r.getObject(1,UUID.class))); }
    @BeforeEach void setup() throws Exception { initial=assets();actor=new Client(admin);slug=createProject();version=1; }
    @AfterEach void remove() throws Exception {
        for(String name:projects) {
            var response=actor.call("GET","/api/admin/projects/"+name,null,true);
            if(response.statusCode()==200) assertEquals(204,actor.call("DELETE","/api/admin/projects/"+name,Map.of("expectedVersion",tree(response).path("version").asLong()),true).statusCode());
        }
        projects.clear();assertEquals(200,actor.call("POST","/api/admin/media/cleanup",null,true).statusCode());
        var created=assets();created.removeAll(initial);
        for(UUID id:created) owner().update("DELETE FROM image_asset WHERE id=?",id);
        owner().update("DELETE FROM project_audit WHERE actor=?",admin);
    }
    @AfterAll void cleanup() { owner().update("DELETE FROM spring_session WHERE principal_name IN (?,?)",admin,viewer);owner().update("DELETE FROM admin_account WHERE username IN (?,?)",admin,viewer); }
    final class Client {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();String csrf;
        Client(String username) throws Exception {
            csrf=tree(call("GET","/api/admin/session",null,false)).path("csrf").path("token").asText();
            var req=HttpRequest.newBuilder(URI.create(base()+"/api/admin/login")).header("X-CSRF-TOKEN",csrf).header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("username="+username+"&password="+password)).build();
            assertEquals(204,http.send(req,HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            csrf=tree(call("GET","/api/admin/session",null,false)).path("csrf").path("token").asText();
        }
        HttpResponse<byte[]> call(String method,String path,Object data,boolean token) throws Exception {
            var req=HttpRequest.newBuilder(URI.create(base()+path));if(token)req.header("X-CSRF-TOKEN",csrf);
            if(data!=null)req.header("Content-Type","application/json");
            return http.send(req.method(method,data==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data))).build(),HttpResponse.BodyHandlers.ofByteArray());
        }
        HttpResponse<byte[]> upload(long expected,String kind,byte[] bytes,String mime,boolean csrf) throws Exception {
            String boundary="image-boundary-"+UUID.randomUUID();var body=new ByteArrayOutputStream();
            for(var field:Map.of("expectedVersion",Long.toString(expected),"kind",kind,"title","Own screenshot","description","Synthetic evidence","alt","Own synthetic image").entrySet())
                body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../untrusted.html\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            var req=HttpRequest.newBuilder(URI.create(base()+"/api/admin/projects/"+slug+"/media")).header("Content-Type","multipart/form-data; boundary="+boundary);
            if(csrf)req.header("X-CSRF-TOKEN",this.csrf);
            return http.send(req.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
        }
    }
    String base() { return "http://127.0.0.1:"+port; }
    JsonNode tree(HttpResponse<byte[]> response) throws Exception { return json.readTree(response.body()); }
    byte[] image(String format,int width,int height) throws Exception {
        var bitmap=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);bitmap.setRGB(0,0,0x449966);var bytes=new ByteArrayOutputStream();assertTrue(ImageIO.write(bitmap,format,bytes));bitmap.flush();return bytes.toByteArray();
    }
    String createProject() throws Exception {
        String name="media-"+UUID.randomUUID();var content=Map.ofEntries(Map.entry("slug",name),Map.entry("title","Media fixture"),Map.entry("subtitle",""),Map.entry("category","全端應用"),Map.entry("status","In Progress"),Map.entry("statusNote",""),Map.entry("summary","Synthetic summary"),Map.entry("description","Synthetic content"),Map.entry("year","2026"),Map.entry("role","Developer"),Map.entry("duration",""),Map.entry("featured",false),Map.entry("theme","forest"),Map.entry("technologyLabel",""),Map.entry("technologies",List.of()),Map.entry("features",List.of()),Map.entry("challenges",List.of()),Map.entry("architecture",List.of()));
        var r=actor.call("POST","/api/admin/projects",Map.of("content",content,"published",false),true);assertEquals(201,r.statusCode());projects.add(name);return name;
    }
    JsonNode upload(String kind,String format) throws Exception {
        var r=actor.upload(version,kind,image(format,20,12),format.equals("png")?"image/png":"image/jpeg",true);assertEquals(200,r.statusCode(),new String(r.body()));version=tree(r).path("version").asLong();return tree(r);
    }
    JsonNode media() throws Exception { return tree(actor.call("GET","/api/admin/projects/"+slug+"/media",null,true)); }
    JsonNode project() throws Exception { return tree(actor.call("GET","/api/admin/projects/"+slug,null,true)); }
    void published(boolean value) throws Exception {
        ObjectNode content=(ObjectNode)project().path("project").deepCopy();content.remove(List.of("coverImage","screenshots","localDemoUrl"));
        var r=actor.call("PUT","/api/admin/projects/"+slug,Map.of("content",content,"published",value,"expectedVersion",version),true);assertEquals(200,r.statusCode());version=tree(r).path("version").asLong();
    }
    int audits() { return owner().queryForObject("SELECT count(*) FROM project_audit WHERE project_slug=?",Integer.class,slug); }
    Path file(JsonNode media,String format) { String id=media.path("src").asText().substring("/api/public/media/".length());return root.resolve("objects").resolve(id+"."+format); }
    @Test void pngIsReencodedFilenameIgnoredAndDraftIsPrivateUntilPublished() throws Exception {
        byte[] bytes=image("png",20,12);var raw=new ByteArrayOutputStream();raw.write(bytes);raw.write("PRIVATE_TRAILING_PAYLOAD".getBytes());
        var r=actor.upload(version,"SCREENSHOT",raw.toByteArray(),"image/png",true);assertEquals(200,r.statusCode());version=tree(r).path("version").asLong();var item=media().get(0);String src=item.path("src").asText();
        assertTrue(src.matches("/api/public/media/[0-9a-f-]{36}"));assertFalse(src.contains("untrusted"));
        assertEquals(404,actor.call("GET",src,null,false).statusCode());
        var privateImage=actor.call("GET",src.replace("/public/","/admin/"),null,true);assertEquals(200,privateImage.statusCode());assertFalse(new String(privateImage.body(),StandardCharsets.ISO_8859_1).contains("PRIVATE_TRAILING_PAYLOAD"));
        assertEquals(20,ImageIO.read(new ByteArrayInputStream(privateImage.body())).getWidth());
        published(true);var publicImage=actor.call("GET",src,null,false);assertEquals(200,publicImage.statusCode());assertEquals("image/png",publicImage.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("no-store",publicImage.headers().firstValue("Cache-Control").orElseThrow());assertEquals("nosniff",publicImage.headers().firstValue("X-Content-Type-Options").orElseThrow());
        published(false);assertEquals(404,actor.call("GET",src,null,false).statusCode());
    }
    @Test void jpegCoverReplacementRetiresOldBlobAndCleanupPreservesCurrentCover() throws Exception {
        upload("COVER","jpg");var old=media().get(0);Path oldFile=file(old,"jpg");assertTrue(Files.exists(oldFile));
        upload("COVER","png");var current=media().get(0);assertEquals(1,media().size());
        assertEquals(404,actor.call("GET",old.path("src").asText().replace("/public/","/admin/"),null,true).statusCode());
        assertEquals(200,actor.call("POST","/api/admin/media/cleanup",null,true).statusCode());assertFalse(Files.exists(oldFile));assertTrue(Files.exists(file(current,"png")));assertEquals(3,audits());
    }
    @Test void fakeSvgMimeMismatchBrokenAndOversizedDimensionsAreRejectedWithoutWrites() throws Exception {
        for(var pair:List.of(new Object[]{"<svg onload='bad'/>".getBytes(),"image/svg+xml"},new Object[]{image("png",20,12),"image/jpeg"},new Object[]{new byte[]{(byte)137,80,78,71,13,10,26,10},"image/png"},new Object[]{image("png",4097,1),"image/png"}))
            assertEquals(400,actor.upload(version,"SCREENSHOT",(byte[])pair[0],(String)pair[1],true).statusCode());
        assertEquals(0,media().size());assertEquals(1,audits());assertEquals(1,project().path("version").asLong());
    }
    @Test void multipartFiveMiBLimitReallyReturns413() throws Exception {
        assertEquals(413,actor.upload(version,"SCREENSHOT",new byte[5242881],"image/png",true).statusCode());assertEquals(0,media().size());assertEquals(1,audits());
    }
    @Test void csrfAndViewerPreventUploadMetadataRemoveAndCleanup() throws Exception {
        assertEquals(403,actor.upload(version,"SCREENSHOT",image("png",20,12),"image/png",false).statusCode());
        upload("SCREENSHOT","png");var item=media().get(0);var v=new Client(viewer);
        assertEquals(200,v.call("GET","/api/admin/projects/"+slug+"/media",null,false).statusCode());
        assertEquals(200,v.call("GET",item.path("src").asText().replace("/public/","/admin/"),null,false).statusCode());
        assertEquals(403,v.upload(version,"SCREENSHOT",image("png",20,12),"image/png",true).statusCode());
        assertEquals(403,v.call("PUT","/api/admin/projects/"+slug+"/media/"+item.path("id").asText(),Map.of("expectedVersion",version),true).statusCode());
        assertEquals(403,v.call("DELETE","/api/admin/projects/"+slug+"/media/"+item.path("id").asText(),Map.of("expectedVersion",version),true).statusCode());
        assertEquals(403,v.call("POST","/api/admin/media/cleanup",null,true).statusCode());
    }
    @Test void staleVersionAndForeignMediaReturn409And404WithoutAnyChanges() throws Exception {
        upload("SCREENSHOT","png");var item=media().get(0);
        assertEquals(409,actor.upload(1,"SCREENSHOT",image("png",20,12),"image/png",true).statusCode());
        assertEquals(409,actor.call("DELETE","/api/admin/projects/"+slug+"/media/"+item.path("id").asText(),Map.of("expectedVersion",1),true).statusCode());
        String old=slug;slug=createProject();version=1;
        assertEquals(404,actor.call("DELETE","/api/admin/projects/"+slug+"/media/"+item.path("id").asText(),Map.of("expectedVersion",version),true).statusCode());
        slug=old;version=2;assertEquals(1,media().size());assertEquals(2,audits());
    }
    @Test void metadataEditingUsesVersionAndPreservesBytesAndAudit() throws Exception {
        upload("SCREENSHOT","png");var item=media().get(0);byte[] before=Files.readAllBytes(file(item,"png"));
        var update=actor.call("PUT","/api/admin/projects/"+slug+"/media/"+item.path("id").asText(),Map.of("expectedVersion",version,"title","New caption","description","Verified","alt","New alt","position",7),true);
        assertEquals(200,update.statusCode());version=tree(update).path("version").asLong();assertEquals(3,version);assertEquals("New caption",media().get(0).path("title").asText());assertArrayEquals(before,Files.readAllBytes(file(item,"png")));assertEquals(3,audits());
    }
    @Test void auditFailureRollsBackNewBlobAndCoverReplacement() throws Exception {
        upload("COVER","png");var old=media().get(0);int count=assets().size();
        owner().execute("REVOKE INSERT ON project_audit FROM portfolio_writer");
        try { assertEquals(503,actor.upload(version,"COVER",image("jpg",20,12),"image/jpeg",true).statusCode()); }
        finally { owner().execute("GRANT INSERT ON project_audit TO portfolio_writer"); }
        assertEquals(old,media().get(0));assertEquals(count,assets().size());assertTrue(Files.exists(file(old,"png")));assertEquals(2,audits());
        try(var files=Files.list(root.resolve("objects"))) { assertEquals(count,files.count()); }
    }
    @Test void storageFailureDoesNotCommitMetadataOrVersion() throws Exception {
        Path objects=root.resolve("objects"),offline=root.resolve("offline");Files.move(objects,offline);
        try { var result=actor.upload(version,"COVER",image("png",20,12),"image/png",true);assertEquals(503,result.statusCode());assertEquals("IMAGE_STORAGE_UNAVAILABLE",tree(result).path("code").asText()); }
        finally { Files.move(offline,objects); }
        assertEquals(1,project().path("version").asLong());assertEquals(0,media().size());assertEquals(1,audits());
    }
    @Test void cleanupIsRetryableAndMissingOrTamperedReferencedBlobIs503() throws Exception {
        upload("SCREENSHOT","png");var item=media().get(0);Path image=file(item,"png");byte[] original=Files.readAllBytes(image);published(true);
        Files.write(image,new byte[]{1,2,3});try { assertEquals(503,actor.call("GET",item.path("src").asText(),null,false).statusCode()); }finally{Files.write(image,original);}
        assertEquals(200,actor.call("DELETE","/api/admin/projects/"+slug+"/media/"+item.path("id").asText(),Map.of("expectedVersion",version),true).statusCode());version++;
        Path objects=root.resolve("objects"),offline=root.resolve("offline");Files.move(objects,offline);
        try { assertEquals(503,actor.call("POST","/api/admin/media/cleanup",null,true).statusCode()); }
        finally { Files.move(offline,objects); }
        assertEquals(200,actor.call("POST","/api/admin/media/cleanup",null,true).statusCode());assertFalse(Files.exists(image));
        assertEquals(0,tree(actor.call("POST","/api/admin/media/cleanup",null,true)).path("retired").asInt());
    }
    @Test void orphanGraceAndTemporaryCleanupNeverRemoveRecentOrReferencedFiles() throws Exception {
        upload("COVER","png");Path linked=file(media().get(0),"png");Files.setLastModifiedTime(linked,FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        Path old=root.resolve("objects").resolve(UUID.randomUUID()+".png"),recent=root.resolve("objects").resolve(UUID.randomUUID()+".png"),temp=root.resolve("incoming").resolve("crash.part");
        Files.write(old,image("png",2,2));Files.write(recent,image("png",2,2));Files.writeString(temp,"Own temporary residue");
        Files.setLastModifiedTime(old,FileTime.from(Instant.now().minus(Duration.ofHours(25))));Files.setLastModifiedTime(temp,FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        try { var result=tree(actor.call("POST","/api/admin/media/cleanup",null,true));assertEquals(1,result.path("orphaned").asInt());assertEquals(1,result.path("temporary").asInt());assertFalse(Files.exists(old));assertFalse(Files.exists(temp));assertTrue(Files.exists(recent));assertTrue(Files.exists(linked)); }
        finally { Files.deleteIfExists(old);Files.deleteIfExists(recent);Files.deleteIfExists(temp); }
    }
    @Test void symbolicFileCannotServeOrDeleteOutsideTheOwnedNamespace() throws Exception {
        upload("COVER","png");var item=media().get(0);Path stored=file(item,"png"),sentinel=root.resolve("sentinel.txt");byte[] bytes=Files.readAllBytes(stored);
        Files.writeString(sentinel,"Sentinel remains private");Files.delete(stored);Files.createSymbolicLink(stored,sentinel);
        try { assertEquals(503,actor.call("GET",item.path("src").asText().replace("/public/","/admin/"),null,false).statusCode());assertEquals("Sentinel remains private",Files.readString(sentinel)); }
        finally { Files.delete(stored);Files.write(stored,bytes);Files.delete(sentinel); }
    }
}
