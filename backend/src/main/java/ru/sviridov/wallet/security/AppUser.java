package ru.sviridov.wallet.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class AppUser implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;

    /**
     * Snapshot of {@code users.password_changed_at} taken at login. Kept in the
     * session (serialisable) so a later password change can be detected without
     * any server-side session registry.
     */
    private final Instant passwordChangedAt;

    public AppUser(Long id, String username, String passwordHash, Instant passwordChangedAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
    }

    public Long getId() {
        return id;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
