-- Adds an optional human-readable name to categorization rules.
ALTER TABLE rules ADD COLUMN name VARCHAR(120);
