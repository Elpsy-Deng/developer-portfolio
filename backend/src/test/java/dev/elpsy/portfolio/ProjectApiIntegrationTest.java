package dev.elpsy.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP on a random loopback port; no mocked controllers or other projects' services. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"portfolio.test-mode=true", "portfolio.auth.cookie-secure=false", "portfolio.media.scheduled-cleanup=false"})
class ProjectApiIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate reader;
    @Autowired FlywayMigrationStrategy migration;

    JdbcTemplate owner() {
        return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),
                System.getenv("PORTFOLIO_MIGRATION_USER"), System.getenv("PORTFOLIO_MIGRATION_PASSWORD")));
    }

    Flyway flyway() {
        return Flyway.configure().dataSource(System.getenv("PORTFOLIO_DB_URL"),
                System.getenv("PORTFOLIO_MIGRATION_USER"), System.getenv("PORTFOLIO_MIGRATION_PASSWORD")).load();
    }

    JsonNode get(String path) {
        var result = http.getForEntity("/api/public/" + path, JsonNode.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("no-store", result.getHeaders().getCacheControl());
        return result.getBody();
    }

    @Test void listHasFiveCuratedSummariesWithoutDetailPayload() {
        var listing = get("projects");
        assertEquals(5, listing.path("total").asInt());
        assertEquals(5, listing.path("catalogTotal").asInt());
        assertEquals(5, listing.path("items").size());
        for (var item : listing.path("items")) {
            assertFalse(item.has("description"));
            assertFalse(item.has("evidenceLinks"));
            assertTrue(item.path("technologies").isArray());
        }
    }

    @Test void featuredFilterKeepsFourWorksAndUnfilteredTotal() {
        var listing = get("projects?featured=true");
        assertEquals(4, listing.path("total").asInt());
        assertEquals(5, listing.path("catalogTotal").asInt());
        for (var item : listing.path("items")) assertTrue(item.path("featured").asBoolean());
        assertEquals("developer-portfolio", get("projects?featured=false").path("items").get(0).path("slug").asText());
    }

    @Test void searchIsCaseInsensitiveAndTrimmed() {
        var listing = http.getForObject("/api/public/projects?q={q}", JsonNode.class, Map.of("q", "  fLuTtEr  "));
        assertEquals(1, listing.path("total").asInt());
        assertEquals("elpsy-shop", listing.path("items").get(0).path("slug").asText());
    }

    @Test void subtitleAndChineseSearchAreSupported() {
        assertEquals("fadaiting-camp", http.getForObject("/api/public/projects?q={q}", JsonNode.class,
                Map.of("q", "露營")).path("items").get(0).path("slug").asText());
    }

    @Test void categoryAndSearchCombineRatherThanBroadenResults() {
        var listing = http.getForObject("/api/public/projects?category={category}&q={q}", JsonNode.class,
                Map.of("category", "後端工程", "q", "Flutter"));
        assertEquals(0, listing.path("total").asInt());
        assertEquals(5, listing.path("catalogTotal").asInt());
        assertTrue(listing.path("items").isEmpty());
    }

    @Test void categoriesAreDerivedFromCurrentCatalog() {
        var categories = get("categories");
        assertEquals(3, categories.size());
        assertEquals("全端應用", categories.get(0).asText());
        assertEquals("後端工程", categories.get(1).asText());
        assertEquals("AI 應用", categories.get(2).asText());
    }

    @Test void detailIncludesOriginalEvidenceAndSustainedThroughputLimit() {
        var detail = get("projects/ticket-platform");
        assertEquals("ticket-platform", detail.path("slug").asText());
        assertEquals(4, detail.path("evidenceLinks").size());
        assertTrue(detail.path("limitations").toString().contains("DIRTY"));
        assertTrue(detail.path("liveDemoUrl").isNull());
        assertEquals("2026-10-12", detail.path("evidenceCheckedAt").asText());
    }

    @Test void unknownOrPausedSlugReturnsExplicitNotFound() {
        for (var slug : new String[]{"unknown", "e-commerce-platform", "rbac-authorization-demo"}) {
            var result = http.getForEntity("/api/public/projects/" + slug, JsonNode.class);
            assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
            assertEquals("PROJECT_NOT_FOUND", result.getBody().path("code").asText());
            assertFalse(result.getBody().has("trace"));
        }
    }

    @Test void invalidFiltersReturn400AndDoNotPretendEmptySuccess() {
        for (var filters : new Map[]{Map.of("q", "x".repeat(121)), Map.of("category", "not-a-category"), Map.of("featured", "yes")}) {
            String key = filters.keySet().iterator().next().toString();
            var result = http.getForEntity("/api/public/projects?" + key + "={value}", JsonNode.class,
                    Map.of("value", filters.get(key)));
            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("INVALID_QUERY", result.getBody().path("code").asText());
        }
    }

    @Test void writeMethodsAreNotExposedAndLeaveCatalogUnchanged() {
        for (var method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE}) {
            var result = http.exchange("/api/public/projects", method,
                    new HttpEntity<>(Map.of("title", "spoofed")), String.class);
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, result.getStatusCode());
        }
        assertEquals(5, get("projects").path("total").asInt());
    }

    @Test void unmatchedSearchIsSuccessfulEmptyListing() {
        var listing = get("projects?q=not-a-real-work");
        assertEquals(0, listing.path("total").asInt());
        assertTrue(listing.path("items").isEmpty());
    }

    @Test void healthConfirmsPostgresReadAndIdentifiesStorage() {
        var health = get("health");
        assertEquals("UP", health.path("status").asText());
        assertEquals("POSTGRESQL", health.path("catalog").asText());
    }

    @Test void runtimeRoleCannotWriteOrCreateSchemaObjects() {
        assertEquals("portfolio_reader", reader.queryForObject("SELECT current_user", String.class));
        assertFalse(reader.queryForObject("SELECT has_table_privilege(current_user,'project','INSERT')", Boolean.class));
        assertFalse(reader.queryForObject("SELECT has_schema_privilege(current_user,'public','CREATE')", Boolean.class));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> reader.update("UPDATE project SET title='forbidden' WHERE slug='developer-portfolio'"));
    }

    @Test void databaseChangesAreVisibleAndMigrationDoesNotReseed() {
        var db = owner();
        var old = db.queryForMap("SELECT title,version FROM project WHERE slug='developer-portfolio'");
        try {
            db.update("UPDATE project SET title='PG persistence probe',version=version+1 WHERE slug='developer-portfolio'");
            assertEquals("PG persistence probe", get("projects/developer-portfolio").path("title").asText());
            assertEquals(0, flyway().migrate().migrationsExecuted);
            assertEquals("PG persistence probe", get("projects/developer-portfolio").path("title").asText());
            assertEquals(5, reader.queryForObject("SELECT count(*) FROM project", Integer.class));
        } finally {
            db.update("UPDATE project SET title=?,version=? WHERE slug='developer-portfolio'", old.get("title"), old.get("version"));
        }
    }

    @Test void draftProjectIsAbsentFromListAndDetail() {
        var db = owner();
        try {
            db.update("UPDATE project SET published=false WHERE slug='developer-portfolio'");
            assertEquals(4, get("projects").path("catalogTotal").asInt());
            assertEquals(HttpStatus.NOT_FOUND, http.getForEntity("/api/public/projects/developer-portfolio", String.class).getStatusCode());
        } finally { db.update("UPDATE project SET published=true WHERE slug='developer-portfolio'"); }
    }

    @Test void storedOrderControlsPublicListDeterministically() {
        var db = owner();
        try {
            db.update("UPDATE project SET sort_order=10 WHERE slug='fadaiting-camp'");
            var listing = get("projects").path("items");
            assertEquals("elpsy-shop", listing.get(0).path("slug").asText());
            assertEquals("fadaiting-camp", listing.get(4).path("slug").asText());
        } finally { db.update("UPDATE project SET sort_order=0 WHERE slug='fadaiting-camp'"); }
    }

    @Test void relationalKeysAndPositionsRejectConflictingRows() {
        var db = owner();
        assertThrows(org.springframework.dao.DataAccessException.class, () -> db.update(
                "INSERT INTO project_technology SELECT * FROM project_technology LIMIT 1"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> db.update(
                "INSERT INTO project_evidence SELECT '00000000-0000-0000-0000-000000000001'::uuid,0,'probe','https://example.com'"));
        assertEquals(14, reader.queryForObject("SELECT count(*) FROM project_evidence", Integer.class));
    }

    @Test void publishedMigrationsAreValidatedAndNotRepeated() {
        assertEquals(9, reader.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        flyway().validate();
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertEquals(5, reader.queryForObject("SELECT count(*) FROM project", Integer.class));
    }

    @Test void latestUpgradePreservesEditedCatalogAndUpdatesOnlyUntouchedPlatformSeed() {
        // Two fresh own schemas exercise real V1/V2 -> V3 upgrade, never synthetic history.
        for (boolean edited : new boolean[]{false, true}) {
            String schema = "upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
            String url = System.getenv("PORTFOLIO_DB_URL") + "?currentSchema=" + schema;
            var dataSource = new DriverManagerDataSource(url, System.getenv("PORTFOLIO_MIGRATION_USER"), System.getenv("PORTFOLIO_MIGRATION_PASSWORD"));
            var db = new JdbcTemplate(dataSource);
            var v2 = Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).target("2").load();
            try {
                assertEquals(2, v2.migrate().migrationsExecuted);
                if (edited) db.update("UPDATE project SET title='My preserved title',version=version+1 WHERE slug='developer-portfolio'");
                String platform = db.queryForObject("SELECT to_jsonb(p)::text FROM project p WHERE slug='developer-portfolio'", String.class);
                String works = db.queryForObject("SELECT jsonb_agg(to_jsonb(p) ORDER BY slug)::text FROM project p WHERE slug<>'developer-portfolio'", String.class);
                var current = Flyway.configure().dataSource(dataSource).defaultSchema(schema).schemas(schema).load();
                assertEquals(7, current.migrate().migrationsExecuted);
                assertEquals(works, db.queryForObject("SELECT jsonb_agg(to_jsonb(p) ORDER BY slug)::text FROM project p WHERE slug<>'developer-portfolio'", String.class));
                if (edited) assertEquals(platform, db.queryForObject("SELECT to_jsonb(p)::text FROM project p WHERE slug='developer-portfolio'", String.class));
                else assertTrue(db.queryForObject("SELECT status_note FROM project WHERE slug='developer-portfolio'", String.class).contains("驗證圖片上傳"));
                assertEquals(0, db.queryForObject("SELECT count(*) FROM admin_account", Integer.class));
                assertEquals(0, current.migrate().migrationsExecuted);
            } finally { owner().execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }

    @Test void v5UpgradePreservesExistingAccountsSessionsAndEditedProject() {
        for (boolean edited : new boolean[]{false, true}) {
            String schema = "upgrade_" + java.util.UUID.randomUUID().toString().replace("-", "");
            var source = new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL") + "?currentSchema=" + schema,
                    System.getenv("PORTFOLIO_MIGRATION_USER"), System.getenv("PORTFOLIO_MIGRATION_PASSWORD"));
            var db = new JdbcTemplate(source);
            var old = Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).target("5").load();
            try {
                assertEquals(5,old.migrate().migrationsExecuted);
                db.update("INSERT INTO admin_account(username,password_hash,role,version) VALUES ('upgrade_user','fixture-not-a-login-hash','ADMIN',7)");
                db.update("INSERT INTO spring_session VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002',1000,2000,1800,1802000,'upgrade_user')");
                db.update("INSERT INTO spring_session_attributes VALUES ('00000000-0000-0000-0000-000000000001','opaque-fixture',decode('010203','hex'))");
                if(edited) db.update("UPDATE project SET title='Preserved V5 title',version=version+1 WHERE slug='developer-portfolio'");
                var statements = java.util.List.of(
                        "SELECT to_jsonb(a)::text FROM admin_account a",
                        "SELECT to_jsonb(s)::text FROM spring_session s",
                        "SELECT to_jsonb(a)::text FROM spring_session_attributes a",
                        "SELECT jsonb_agg(to_jsonb(p) ORDER BY slug)::text FROM project p WHERE slug<>'developer-portfolio'",
                        "SELECT jsonb_agg(to_jsonb(m) ORDER BY project_id,position)::text FROM project_media m");
                var snapshots = statements.stream().map(q->db.queryForObject(q,String.class)).toList();
                String platform=db.queryForObject("SELECT to_jsonb(p)::text FROM project p WHERE slug='developer-portfolio'",String.class);
                var current=Flyway.configure().dataSource(source).defaultSchema(schema).schemas(schema).load();
                assertEquals(4,current.migrate().migrationsExecuted);
                for(int i=0;i<statements.size();i++) assertEquals(snapshots.get(i),db.queryForObject(statements.get(i),String.class));
                if(edited) assertEquals(platform,db.queryForObject("SELECT to_jsonb(p)::text FROM project p WHERE slug='developer-portfolio'",String.class));
                else assertTrue(db.queryForObject("SELECT status_note FROM project WHERE slug='developer-portfolio'",String.class).contains("持久登入限流"));
                assertEquals(0,db.queryForObject("SELECT count(*) FROM admin_login_bucket",Integer.class));
                assertEquals(0,current.migrate().migrationsExecuted);
            } finally { owner().execute("DROP SCHEMA " + schema + " CASCADE"); }
        }
    }

    @Test void wrongDatabaseIsRejectedBeforeHistoryCreation() {
        String url = System.getenv("PORTFOLIO_DB_URL").replace("/portfolio_test", "/postgres");
        var db = new JdbcTemplate(new DriverManagerDataSource(url, "portfolio_bootstrap", System.getenv("PORTFOLIO_BOOTSTRAP_PASSWORD")));
        assertNull(db.queryForObject("SELECT to_regclass('public.flyway_schema_history')", String.class));
        assertThrows(IllegalStateException.class, () -> migration.migrate(Flyway.configure()
                .dataSource(url, "portfolio_bootstrap", System.getenv("PORTFOLIO_BOOTSTRAP_PASSWORD")).load()));
        assertNull(db.queryForObject("SELECT to_regclass('public.flyway_schema_history')", String.class));
    }
}
