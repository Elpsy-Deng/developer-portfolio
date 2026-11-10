package dev.jackteng.portfolio.operations;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Reject privileged runtime configuration before any datasource/Flyway initialization. */
@Configuration @Profile("deploy")
public class DeploymentConfiguration {
    @Bean public static BeanFactoryPostProcessor runtimeCredentialsOnly(Environment env) {
        return factory -> {
            if (env.getProperty("spring.flyway.enabled", Boolean.class, true))
                throw new IllegalStateException("Deployment API must not run migrations");
            for (String key : java.util.List.of("PORTFOLIO_MIGRATION_USER", "PORTFOLIO_MIGRATION_PASSWORD",
                    "spring.flyway.user", "spring.flyway.password", "PORTFOLIO_BOOTSTRAP_PASSWORD"))
                if (!env.getProperty(key, "").isBlank()) throw new IllegalStateException("Privileged credentials forbidden in deployment API");
            if (!"portfolio_reader".equals(env.getProperty("spring.datasource.username")))
                throw new IllegalStateException("Deployment public datasource must use dedicated reader");
        };
    }
    @Bean public InitializingBean migratedSchemaRequired(@Qualifier("dataSource") DataSource reader) {
        return () -> {
            try (var connection = reader.getConnection(); var query = connection.createStatement();
                    var rows = query.executeQuery("SELECT current_database(), current_user")) {
                if (!rows.next() || !java.util.Set.of("portfolio_demo", "portfolio_test").contains(rows.getString(1))
                        || !"portfolio_reader".equals(rows.getString(2)))
                    throw new IllegalStateException("Dedicated deployment reader/database required");
            }
            // Validate with the SELECT-only connection; never migrate, baseline, clean or repair.
            Flyway.configure().dataSource(reader).locations("classpath:db/migration")
                    .cleanDisabled(true).baselineOnMigrate(false).ignoreMigrationPatterns(new String[0]).load().validate();
        };
    }
}
