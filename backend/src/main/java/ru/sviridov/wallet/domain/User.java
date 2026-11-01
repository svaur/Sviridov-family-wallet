package ru.sviridov.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Application user. Credentials ({@code username} login + BCrypt
 * {@code passwordHash}) plus an optional human-readable name that the sidebar
 * shows instead of the login. Users are created manually (bootstrap env var,
 * SQL, or the admin panel); there is no self-registration.
 *
 * <p>{@code sessionEpoch} is an epoch, not just an audit field: it is
 * snapshotted into the authentication principal at login and compared on every
 * request, so bumping it signs out the other devices without any server-side
 * session registry. A password change, a ban/unban, an admin password reset and
 * a forced logout all bump it.</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", length = 64)
    private String firstName;

    @Column(name = "last_name", length = 64)
    private String lastName;

    @Column(name = "session_epoch", nullable = false)
    private Instant sessionEpoch;

    /** Set by an admin after a password reset; the wallet forces a change. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** A banned user cannot sign in; existing sessions are killed via the epoch. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
        this.sessionEpoch = this.createdAt;
        this.isActive = true;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Instant getSessionEpoch() {
        return sessionEpoch;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Blank names arrive as {@code null}; trimming is the service's job. */
    public void setName(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    /** Callers encode the password; bumping the epoch is exactly the point. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.sessionEpoch = Instant.now();
        this.mustChangePassword = false;
    }

    /** Admin: force the user to change the password on next login. */
    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    /** Admin: ban/unban. */
    public void setActive(boolean active) {
        this.isActive = active;
    }

    /** Admin: sign out every session (ban, unban, reset, forced logout). */
    public void bumpSessionEpoch() {
        this.sessionEpoch = Instant.now();
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
