-- Stage 8: a system category that excludes its transactions from analytics.
-- "исключено" is a normal category (assignable by a rule or by hand), but its
-- rows are dropped from the dashboard; is_excluded carries that meaning so the
-- analytics filter never depends on a human-editable name.
ALTER TABLE categories ADD COLUMN is_excluded BOOLEAN NOT NULL DEFAULT false;

-- Seed one "исключено" category per existing user (new users get it lazily on
-- first category list).
INSERT INTO categories (user_id, parent_id, name, icon, is_excluded)
SELECT u.id, NULL, 'исключено', '🚫', true
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND c.is_excluded = true
);
