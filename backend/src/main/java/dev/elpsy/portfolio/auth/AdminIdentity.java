package dev.elpsy.portfolio.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Serialized into JDBC sessions without retaining the password hash after login. */
public final class AdminIdentity implements UserDetails, CredentialsContainer, Serializable {
    private static final long serialVersionUID = 1L;
    private final String username;
    private String password;
    private final String role;
    private final long version;
    private final boolean enabled;
    public AdminIdentity(String username, String password, String role, long version, boolean enabled) {
        this.username = username; this.password = password; this.role = role; this.version = version; this.enabled = enabled;
    }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String role() { return role; }
    public long version() { return version; }
    public boolean isEnabled() { return enabled; }
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    public void eraseCredentials() { password = null; }
}
