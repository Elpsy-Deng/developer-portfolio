package dev.elpsy.portfolio.catalog;

import java.util.Set;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class CatalogDatabaseGuard {
    @Bean
    public FlywayMigrationStrategy ownedDatabaseMigration(@Value("${portfolio.test-mode:false}") boolean testMode) {
        return flyway -> {
            // Check before Flyway creates history or writes any schema objects.
            try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT current_database()")) {
                if (!rows.next() || !Set.of("portfolio_demo", "portfolio_test").contains(rows.getString(1))
                        || testMode && !"portfolio_test".equals(rows.getString(1))) {
                    throw new IllegalStateException("Explicit dedicated portfolio database required");
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException("Cannot verify dedicated portfolio database", e);
            }
            flyway.migrate();
        };
    }
}
