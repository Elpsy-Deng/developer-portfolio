package dev.jackteng.portfolio.auth;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.session.jdbc.config.annotation.SpringSessionTransactionManager;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class AuthDatabase {
    @Bean @Primary @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean @SpringSessionDataSource
    HikariDataSource authDataSource(@Value("${spring.datasource.url}") String url,
            @Value("${portfolio.auth.username}") String user, @Value("${portfolio.auth.password}") String password) {
        var pool = new HikariDataSource();
        pool.setJdbcUrl(url); pool.setUsername(user); pool.setPassword(password);
        pool.setMaximumPoolSize(4); pool.setConnectionTimeout(2000); pool.setValidationTimeout(1000);
        pool.addDataSourceProperty("connectTimeout", 2); pool.addDataSourceProperty("socketTimeout", 3);
        return pool;
    }
    @Bean @Primary JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource ds) { return new JdbcTemplate(ds); }
    @Bean JdbcTemplate authJdbc(@Qualifier("authDataSource") DataSource ds) { return new JdbcTemplate(ds); }
    @Bean @Primary PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
    @Bean @SpringSessionTransactionManager
    PlatformTransactionManager authTransactionManager(@Qualifier("authDataSource") DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }
    @Bean DefaultCookieSerializer cookieSerializer(@Value("${portfolio.auth.cookie-secure}") boolean secure) {
        var cookie = new DefaultCookieSerializer();
        cookie.setCookieName("PORTFOLIO_SESSION"); cookie.setCookiePath("/api/admin");
        cookie.setUseHttpOnlyCookie(true); cookie.setUseSecureCookie(secure); cookie.setSameSite("Strict");
        return cookie;
    }
}
