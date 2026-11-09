package dev.elpsy.portfolio.auth;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class IdentityDatabase {
    @Bean HikariDataSource identityDataSource(@Value("${spring.datasource.url}") String url,
            @Value("${portfolio.identity.username}") String user, @Value("${portfolio.identity.password}") String password) {
        var pool = new HikariDataSource();
        pool.setJdbcUrl(url); pool.setUsername(user); pool.setPassword(password);
        pool.setMaximumPoolSize(4); pool.setConnectionTimeout(2000); pool.setValidationTimeout(1000);
        pool.addDataSourceProperty("connectTimeout", 2); pool.addDataSourceProperty("socketTimeout", 3);
        return pool;
    }
    @Bean JdbcTemplate identityJdbc(@Qualifier("identityDataSource") javax.sql.DataSource ds) { return new JdbcTemplate(ds); }
    @Bean PlatformTransactionManager identityTransactionManager(@Qualifier("identityDataSource") javax.sql.DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
