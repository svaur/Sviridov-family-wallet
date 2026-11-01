-- Human-readable name shown in the sidebar (the login itself never changes).
ALTER TABLE users
    ADD COLUMN first_name VARCHAR(64),
    ADD COLUMN last_name  VARCHAR(64);

-- Epoch of the last password change. The value is snapshotted into the
-- authentication principal at login; a session whose snapshot is stale is
-- rejected, which is how "change password" signs out every device without
-- keeping a server-side session registry. NOT NULL DEFAULT now() also keeps the
-- `INSERT INTO users (username, password_hash)` from the README working.
ALTER TABLE users
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();
