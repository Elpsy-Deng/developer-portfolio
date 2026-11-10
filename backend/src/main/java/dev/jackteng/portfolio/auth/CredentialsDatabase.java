package dev.jackteng.portfolio.auth;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CredentialsDatabase {
    @Bean HikariDataSource credentialsDataSource(@Value("${spring.datasource.url}") String url,
            @Value("${portfolio.credentials.username}") String user, @Value("${portfolio.credentials.password}") String password) {
        var pool = new HikariDataSource();
        pool.setJdbcUrl(url); pool.setUsername(user); pool.setPassword(password);
        pool.setMaximumPoolSize(4); pool.setConnectionTimeout(2000); pool.setValidationTimeout(1000);
        pool.addDataSourceProperty("connectTimeout", 2); pool.addDataSourceProperty("socketTimeout", 3);
        return pool;
    }
    @Bean JdbcTemplate credentialsJdbc(@Qualifier("credentialsDataSource") javax.sql.DataSource ds) { return new JdbcTemplate(ds); }
    @Bean PlatformTransactionManager credentialsTransactionManager(@Qualifier("credentialsDataSource") javax.sql.DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
}
