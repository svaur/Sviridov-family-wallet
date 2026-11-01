-- V10: allow a 'screenshot' source for bank-feed (screenshot) imports.
ALTER TABLE transactions DROP CONSTRAINT transactions_source_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_source_check
    CHECK (source IN ('statement', 'screenshot', 'card', 'cash', 'other'));
