package dev.elpsy.portfolio.operations;

import java.nio.file.*;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import dev.elpsy.portfolio.catalog.CatalogDatabaseGuard;

/** One-shot deployment entry point: no web, sessions, runtime writers or schedulers. */
public final class CatalogMigration {
    private CatalogMigration() {}
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || args.length == 2 && !args[1].equals("validate"))
            throw new IllegalArgumentException("One private migration properties file and optional validate required");
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of(args[0]))) { properties.load(input); }
        if (args.length == 2) configured(properties).validate(); else migrate(properties);
    }
    public static int migrate(Properties properties) {
        var flyway=configured(properties);
        new CatalogDatabaseGuard().ownedDatabaseMigration(false).migrate(flyway);
        flyway.validate();
        return flyway.info().applied().length;
    }
    private static Flyway configured(Properties properties) {
        if (!properties.stringPropertyNames().equals(java.util.Set.of("url", "user", "password"))
                || !"portfolio_owner".equals(properties.getProperty("user"))
                || properties.getProperty("password", "").isBlank())
            throw new IllegalArgumentException("Dedicated migration credentials required");
        return Flyway.configure().dataSource(properties.getProperty("url"), properties.getProperty("user"),
                properties.getProperty("password")).locations("classpath:db/migration")
                .cleanDisabled(true).baselineOnMigrate(false).ignoreMigrationPatterns(new String[0]).load();
    }
}
