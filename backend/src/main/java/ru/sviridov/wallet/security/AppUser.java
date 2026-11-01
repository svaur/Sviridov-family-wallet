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
     * Snapshot of {@code users.session_epoch} taken at login. Kept in the
     * session (serialisable) so a later password change, ban or reset can be
     * detected without any server-side session registry.
     */
    private final Instant sessionEpoch;

    private final boolean enabled;

    /**
     * Snapshot of {@code users.must_change_password} taken at login. A filter
     * rejects every wallet call (except the forced change itself) while this is
     * true, and a password change clears the flag on the user and bumps the
     * epoch — so the next login carries {@code false}.
     */
    private final boolean mustChangePassword;

    public AppUser(Long id, String username, String passwordHash, Instant sessionEpoch,
                   boolean enabled, boolean mustChangePassword) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.sessionEpoch = sessionEpoch;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getId() {
        return id;
    }

    public Instant getSessionEpoch() {
        return sessionEpoch;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
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
        return enabled;
    }
}
