package dev.jackteng.portfolio;

import java.util.Properties;
import dev.jackteng.portfolio.operations.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"portfolio.test-mode=true","portfolio.media.scheduled-cleanup=false"})
class DeploymentIntegrationTest {
    @Autowired @Qualifier("dataSource") javax.sql.DataSource reader;
    @Autowired JdbcTemplate db;
    Properties migration() {
        var p=new Properties();p.setProperty("url",System.getenv("PORTFOLIO_DB_URL"));
        p.setProperty("user",System.getenv("PORTFOLIO_MIGRATION_USER"));
        p.setProperty("password",System.getenv("PORTFOLIO_MIGRATION_PASSWORD"));return p;
    }
    JdbcTemplate owner() { return new JdbcTemplate(new DriverManagerDataSource(System.getenv("PORTFOLIO_DB_URL"),
            System.getenv("PORTFOLIO_MIGRATION_USER"),System.getenv("PORTFOLIO_MIGRATION_PASSWORD"))); }
    String history() {return db.queryForObject("SELECT jsonb_agg(to_jsonb(x) ORDER BY installed_rank)::text FROM flyway_schema_history x",String.class);}
    @Test void oneShotMigrationAndReadonlyValidationPreserveExistingHistory() throws Exception {
        String before=history();assertEquals(9,CatalogMigration.migrate(migration()));
        new DeploymentConfiguration().migratedSchemaRequired(reader).afterPropertiesSet();
        assertEquals(before,history());
    }
    @Test void unknownFutureMigrationHistoryRefusesReadonlyRuntime() {
        String before=history();var owner=owner();
        try {
            owner.update("INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) VALUES(100,'100','synthetic future','SQL','V100__synthetic.sql',1,'test',0,true)");
            assertThrows(org.flywaydb.core.api.exception.FlywayValidateException.class,
                    ()->new DeploymentConfiguration().migratedSchemaRequired(reader).afterPropertiesSet());
        } finally {owner.update("DELETE FROM flyway_schema_history WHERE installed_rank=100");}
        assertEquals(before,history());
    }
    @Test void checksumMismatchRefusesRuntimeWithoutRepairingHistory() {
        var owner=owner();String before=history();int checksum=db.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'",Integer.class);
        try {
            owner.update("UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='1'");
            assertThrows(org.flywaydb.core.api.exception.FlywayValidateException.class,
                    ()->new DeploymentConfiguration().migratedSchemaRequired(reader).afterPropertiesSet());
            assertEquals(checksum+1,db.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'",Integer.class));
        } finally {owner.update("UPDATE flyway_schema_history SET checksum=? WHERE version='1'",checksum);}
        assertEquals(before,history());
    }
    @Test void apiConfigurationRejectsMigrationAndBootstrapCredentialsBeforeInitialization() {
        for(String key:java.util.List.of("PORTFOLIO_MIGRATION_USER","PORTFOLIO_MIGRATION_PASSWORD","PORTFOLIO_BOOTSTRAP_PASSWORD","spring.flyway.user","spring.flyway.password")) {
            var env=new org.springframework.mock.env.MockEnvironment().withProperty("spring.flyway.enabled","false")
                    .withProperty("spring.datasource.username","portfolio_reader").withProperty(key,"synthetic forbidden");
            assertThrows(IllegalStateException.class,()->DeploymentConfiguration.runtimeCredentialsOnly(env).postProcessBeanFactory(null));
        }
    }
    @Test void apiConfigurationRejectsEnabledFlywayAndOwnerDatasource() {
        var env=new org.springframework.mock.env.MockEnvironment().withProperty("spring.flyway.enabled","true")
                .withProperty("spring.datasource.username","portfolio_reader");
        assertThrows(IllegalStateException.class,()->DeploymentConfiguration.runtimeCredentialsOnly(env).postProcessBeanFactory(null));
        env.withProperty("spring.flyway.enabled","false").withProperty("spring.datasource.username","portfolio_owner");
        assertThrows(IllegalStateException.class,()->DeploymentConfiguration.runtimeCredentialsOnly(env).postProcessBeanFactory(null));
    }
    @Test void migratorRejectsRuntimeRoleAndUnexpectedSecretFields() {
        var runtime=migration();runtime.setProperty("user","portfolio_reader");assertThrows(IllegalArgumentException.class,()->CatalogMigration.migrate(runtime));
        var invalid=migration();invalid.setProperty("runtime.secret","synthetic");assertThrows(IllegalArgumentException.class,()->CatalogMigration.migrate(invalid));
    }
}
