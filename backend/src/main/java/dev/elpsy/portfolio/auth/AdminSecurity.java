package dev.elpsy.portfolio.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class AdminSecurity {
    @Bean PasswordEncoder passwordEncoder() {
        // Matches the explicit provisioning CLI; salt=16 bytes, PBKDF2-SHA256, 310000 rounds.
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
    @Bean @Order(1) SecurityFilterChain publicApi(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/public/**").csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }
    @Bean @Order(2) SecurityFilterChain adminApi(HttpSecurity http, AdminAccounts accounts, ObjectMapper json, LoginLimits limits, PeerAddresses peers,
            @Value("${portfolio.auth.absolute-seconds:28800}") long absoluteSeconds) throws Exception {
        var csrfTokens = new HttpSessionCsrfTokenRepository();
        return http.securityMatcher("/api/admin/**")
                .csrf(c -> c.csrfTokenRepository(csrfTokens))
                .authorizeHttpRequests(a -> a.requestMatchers("/api/admin/session", "/api/admin/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/projects", "/api/admin/projects/**", "/api/admin/catalog-options", "/api/admin/media/**").hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/taxonomy/**").hasAnyRole("ADMIN","VIEWER")
                        .requestMatchers("/api/admin/taxonomy/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/projects", "/api/admin/projects/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/media/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/password").hasAnyRole("ADMIN", "VIEWER")
                        .requestMatchers("/api/admin/security").hasRole("ADMIN")
                        .requestMatchers("/api/admin/accounts", "/api/admin/accounts/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/overview").hasAnyRole("ADMIN", "VIEWER")
                        .anyRequest().denyAll())
                .requestCache(c -> c.disable())
                .addFilterBefore(new AnonymousSessionFilter(limits, peers, json), CsrfFilter.class)
                .addFilterBefore(new AbsoluteSessionFilter(absoluteSeconds, json), AnonymousSessionFilter.class)
                .addFilterBefore(new LoginProtectionFilter(limits, peers, json), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AccountValidityFilter(accounts), AnonymousAuthenticationFilter.class)
                .formLogin(f -> f.loginProcessingUrl("/api/admin/login")
                        .successHandler((req, res, auth) -> {
                            req.getSession().setAttribute(AbsoluteSessionFilter.LOGIN_TIME, System.currentTimeMillis());
                            // Commit the rotated token with login, before parallel session readers arrive.
                            csrfTokens.saveToken(csrfTokens.generateToken(req), req, res);
                            res.setStatus(204); res.setHeader("Cache-Control", "no-store"); })
                        .failureHandler((req, res, error) -> AuthResponses.error(json, res,
                                error instanceof InternalAuthenticationServiceException ? 503 : 401,
                                error instanceof InternalAuthenticationServiceException ? "AUTH_UNAVAILABLE" : "INVALID_CREDENTIALS")))
                .logout(l -> l.logoutUrl("/api/admin/logout").deleteCookies("PORTFOLIO_SESSION")
                        .logoutSuccessHandler((req, res, auth) -> { res.setStatus(204); res.setHeader("Cache-Control", "no-store"); }))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, error) -> AuthResponses.error(json, res, 401, "UNAUTHENTICATED"))
                        .accessDeniedHandler((req, res, error) -> AuthResponses.error(json, res, 403,
                                error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN")))
                .build();
    }
    @Bean @Order(3) SecurityFilterChain otherwise(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.requestMatchers("/error").permitAll().anyRequest().denyAll()).build();
    }
}
