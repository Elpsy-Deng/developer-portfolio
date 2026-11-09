package dev.elpsy.portfolio.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AdminAccounts implements UserDetailsService {
    private final JdbcTemplate db;
    public AdminAccounts(@Qualifier("authJdbc") JdbcTemplate db) { this.db = db; }
    public AdminIdentity loadUserByUsername(String username) {
        var rows = db.query("SELECT username,password_hash,role,version,enabled FROM admin_account WHERE username=?",
                (r, n) -> new AdminIdentity(r.getString(1), r.getString(2), r.getString(3), r.getLong(4), r.getBoolean(5)), username);
        if (rows.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return rows.getFirst();
    }
    public int enabledAccounts() { return db.queryForObject("SELECT count(*) FROM admin_account WHERE enabled", Integer.class); }
}
