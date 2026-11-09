package dev.elpsy.portfolio;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.*;
import dev.elpsy.portfolio.auth.AdminIdentity;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, cookie jar and actual PostgreSQL sessions, not mocked Spring Security. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"portfolio.test-mode=true", "portfolio.auth.cookie-secure=false", "portfolio.media.scheduled-cleanup=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAuthIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired @Qualifier("authJdbc") JdbcTemplate authDb;
    @Autowired JdbcTemplate reader;
    private final String password = UUID.randomUUID().toString();
    private final String admin = "test_admin_" + UUID.randomUUID().toString().replace("-", "");
    private final String viewer = "test_viewer_" + UUID.randomUUID().toString().replace("-", "");
    JdbcTemplate owner() { return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),
            System.getenv("PORTFOLIO_MIGRATION_USER"), System.getenv("PORTFOLIO_MIGRATION_PASSWORD"))); }
    @BeforeAll void provision() {
        owner().update("INSERT INTO admin_account(username,password_hash,role) VALUES (?,?,'ADMIN'),(?,?,'VIEWER')",
                admin, encoder.encode(password), viewer, encoder.encode(password));
    }
    @AfterAll void clean() {
        owner().update("DELETE FROM spring_session WHERE principal_name IN (?,?)", admin, viewer);
        owner().update("DELETE FROM admin_account WHERE username IN (?,?)", admin, viewer);
    }
    final class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> call(String method, String path, String body, String csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin/" + path)).timeout(Duration.ofSeconds(10));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            if (body != null) request.header("Content-Type", "application/x-www-form-urlencoded");
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode session() throws Exception {
            var result = call("GET", "session", null, null); assertEquals(200, result.statusCode());
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            return json.readTree(result.body());
        }
        String csrf() throws Exception { return session().path("csrf").path("token").asText(); }
        String cookie() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("PORTFOLIO_SESSION")).findFirst().orElseThrow().getValue(); }
        void login(String username) throws Exception {
            var result = call("POST", "login", "username=" + username + "&password=" + password, csrf());
            assertEquals(204, result.statusCode());
        }
    }
    @Test void anonymousReadGets401AndSessionHasOnlyAnonymousIdentityAndCsrf() throws Exception {
        var browser = new Browser();
        assertEquals(401, browser.call("GET", "overview", null, null).statusCode());
        var session = browser.session();
        assertFalse(session.path("authenticated").asBoolean()); assertEquals("", session.path("role").asText());
        assertEquals("X-CSRF-TOKEN", session.path("csrf").path("headerName").asText());
        assertTrue(session.path("csrf").path("token").asText().length() > 32);
    }
    @Test void loginAndLogoutRejectMissingAndForgedCsrfWithoutSideEffects() throws Exception {
        var browser = new Browser(); browser.session();
        for (String token : new String[]{null, "forged"}) {
            var denied = browser.call("POST", "login", "username=" + admin + "&password=" + password, token);
            assertEquals(403, denied.statusCode()); assertEquals("CSRF_INVALID", json.readTree(denied.body()).path("code").asText());
        }
        assertFalse(browser.session().path("authenticated").asBoolean());
        browser.login(admin);
        assertEquals(403, browser.call("POST", "logout", null, null).statusCode());
        assertTrue(browser.session().path("authenticated").asBoolean());
    }
    @Test void unknownWrongPasswordAndDisabledAccountAllReturnSameGeneric401() throws Exception {
        var browser = new Browser();
        for (String username : new String[]{admin, "no_such_account"}) {
            var denied = browser.call("POST", "login", "username=" + username + "&password=wrong", browser.csrf());
            assertEquals(401, denied.statusCode()); assertEquals("INVALID_CREDENTIALS", json.readTree(denied.body()).path("code").asText());
            assertFalse(denied.body().contains(username));
        }
        try {
            owner().update("UPDATE admin_account SET enabled=false WHERE username=?", admin);
            var denied = browser.call("POST", "login", "username=" + admin + "&password=" + password, browser.csrf());
            assertEquals(401, denied.statusCode()); assertEquals("INVALID_CREDENTIALS", json.readTree(denied.body()).path("code").asText());
        } finally { owner().update("UPDATE admin_account SET enabled=true WHERE username=?", admin); }
    }
    @Test void loginRotatesSessionCookieAndCsrfAndStoresNoPasswordInSession() throws Exception {
        var browser = new Browser(); String oldCsrf = browser.csrf(), oldCookie = browser.cookie();
        var result = browser.call("POST", "login", "username=" + admin + "&password=" + password, oldCsrf);
        assertEquals(204, result.statusCode()); assertNotEquals(oldCookie, browser.cookie());
        var session = browser.session(); assertTrue(session.path("authenticated").asBoolean());
        assertEquals(admin, session.path("username").asText()); assertEquals("ADMIN", session.path("role").asText());
        assertEquals(403, browser.call("POST", "logout", null, oldCsrf).statusCode());
        String hash = owner().queryForObject("SELECT password_hash FROM admin_account WHERE username=?", String.class, admin);
        for (byte[] bytes : owner().query("SELECT a.attribute_bytes FROM spring_session_attributes a JOIN spring_session s ON s.primary_id=a.session_primary_id WHERE s.principal_name=?",
                (r,n) -> r.getBytes(1), admin)) {
            String serialized = new String(bytes, StandardCharsets.ISO_8859_1);
            assertFalse(serialized.contains(hash)); assertFalse(serialized.contains(password));
        }
        assertFalse(session.toString().contains(hash)); assertFalse(session.has("password"));
    }
    @Test void concurrentFirstSessionReadsAfterLoginKeepBothCsrfTokensUsable() throws Exception {
        var browser = new Browser(); String previousToken = browser.csrf();
        assertEquals(204, browser.call("POST", "login", "username=" + admin + "&password=" + password, previousToken).statusCode());
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin/session"))
                .header("Cookie", "PORTFOLIO_SESSION=" + browser.cookie()).timeout(Duration.ofSeconds(10)).GET().build();
        java.util.concurrent.CompletableFuture<HttpResponse<String>> first, second;
        // Allow both readers to load the same session, but hold any new attribute INSERT.
        // The unfixed login leaves CSRF absent: one INSERT then fails with a duplicate key.
        try (var barrier = owner().getDataSource().getConnection()) {
            barrier.setAutoCommit(false);
            barrier.createStatement().execute("LOCK TABLE spring_session_attributes IN SHARE MODE");
            first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            try {
                while (!(first.isDone() && second.isDone())) {
                    int waiting = authDb.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() "
                            + "AND usename='portfolio_auth' AND wait_event_type='Lock' AND query ILIKE '%SPRING_SESSION%'", Integer.class);
                    if (waiting >= 2) break;
                    assertTrue(System.nanoTime() < deadline, "Both session reads must finish or reach the controlled database barrier");
                    Thread.sleep(10);
                }
            } finally { barrier.rollback(); }
        }
        for (var future : java.util.List.of(first, second)) {
            var response = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(200, response.statusCode(), "Concurrent session read must not fail saving CSRF");
            var session = json.readTree(response.body());
            assertTrue(session.path("authenticated").asBoolean());
            var denied = browser.call("POST", "not-implemented", "", session.path("csrf").path("token").asText());
            assertEquals(403, denied.statusCode());
            assertEquals("FORBIDDEN", json.readTree(denied.body()).path("code").asText(), "Every returned token must pass CSRF before endpoint authorization");
        }
        assertEquals("CSRF_INVALID", json.readTree(browser.call("POST", "not-implemented", "", previousToken).body()).path("code").asText());
        assertTrue(browser.session().path("authenticated").asBoolean());
    }
    @Test void cookieIsHttpOnlyStrictAndScopedAndResponsesHaveSecurityHeaders() throws Exception {
        var response = new Browser().call("GET", "session", null, null);
        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Strict")); assertTrue(cookie.contains("Path=/api/admin"));
        assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElseThrow());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
    }
    @Test void viewerCanReadOverviewButCannotSpoofAdminAndAdminCanReadSecurity() throws Exception {
        var browser = new Browser(); browser.login(viewer);
        assertEquals(200, browser.call("GET", "overview", null, null).statusCode());
        var denied = browser.call("GET", "security?role=ADMIN&username=" + admin, null, null);
        assertEquals(403, denied.statusCode()); assertEquals("FORBIDDEN", json.readTree(denied.body()).path("code").asText());
        var allowed = new Browser(); allowed.login(admin);
        var response = allowed.call("GET", "security", null, null);
        assertEquals(200, response.statusCode()); assertEquals(30, json.readTree(response.body()).path("idleTimeoutMinutes").asInt());
        assertEquals(403, allowed.call("POST", "not-implemented", "title=spoofed", allowed.csrf()).statusCode());
        assertEquals(5, reader.queryForObject("SELECT count(*) FROM project", Integer.class));
    }
    @Test void logoutRevokesDatabaseSessionAndOldCookieCannotBeReused() throws Exception {
        var browser = new Browser(); browser.login(admin); String cookie = browser.cookie(), token = browser.csrf();
        assertEquals(204, browser.call("POST", "logout", null, token).statusCode());
        var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin/overview"))
                .header("Cookie", "PORTFOLIO_SESSION=" + cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, replay.statusCode()); assertFalse(browser.session().path("authenticated").asBoolean());
    }
    @Test void realDatabaseExpiryInvalidatesSession() throws Exception {
        var browser = new Browser(); browser.login(viewer);
        owner().update("UPDATE spring_session SET expiry_time=0,last_access_time=0 WHERE principal_name=?", viewer);
        assertEquals(401, browser.call("GET", "overview", null, null).statusCode());
    }
    @Test void roleChangeAndAccountDisableImmediatelyRevokeOldSessions() throws Exception {
        var browser = new Browser(); browser.login(admin);
        try {
            owner().update("UPDATE admin_account SET role='VIEWER' WHERE username=?", admin);
            assertEquals(401, browser.call("GET", "security", null, null).statusCode());
            browser.login(admin); assertEquals(403, browser.call("GET", "security", null, null).statusCode());
            owner().update("UPDATE admin_account SET enabled=false WHERE username=?", admin);
            assertEquals(401, browser.call("GET", "overview", null, null).statusCode());
        } finally { owner().update("UPDATE admin_account SET role='ADMIN',enabled=true WHERE username=?", admin); }
    }
    @Test void credentialVersionChangeRevokesSessionAndCredentialEraseRetainsOnlyIdentity() throws Exception {
        var browser = new Browser(); browser.login(viewer);
        owner().update("UPDATE admin_account SET version=version+1 WHERE username=?", viewer);
        assertEquals(401, browser.call("GET", "overview", null, null).statusCode());
        var principal = new AdminIdentity("probe", "secret", "VIEWER", 1, true);
        principal.eraseCredentials(); assertNull(principal.getPassword());
    }
    @Test void authDatabaseRoleCannotWriteAccountsCatalogOrSchemaAndReaderCannotReadCredentials() {
        assertEquals("portfolio_auth", authDb.queryForObject("SELECT current_user", String.class));
        for (String statement : new String[]{"UPDATE admin_account SET role='ADMIN'", "UPDATE project SET title='forbidden'", "CREATE TABLE forbidden(id int)"})
            assertThrows(org.springframework.dao.DataAccessException.class, () -> authDb.execute(statement));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> reader.queryForList("SELECT * FROM admin_account"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> reader.queryForList("SELECT * FROM spring_session_attributes"));
    }
}
