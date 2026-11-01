-- Audit must survive user deletion, and each entry now records WHO acted (an
-- admin acting for the server, or the user themselves) and from which IP.

ALTER TABLE audit_log
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE audit_log
    DROP CONSTRAINT audit_log_user_id_fkey;

ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE audit_log
    ADD COLUMN actor_kind VARCHAR(16) NOT NULL DEFAULT 'user',
    ADD COLUMN actor_id   BIGINT,
    ADD COLUMN ip         VARCHAR(45);

CREATE INDEX idx_audit_log_at ON audit_log (at DESC);
