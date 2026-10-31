-- V5: record a transaction's funding source and relax the card requirement.
-- source: 'statement' (imported from a bank statement, card_id set),
--         'card' (manual, tied to a card), 'cash' (наличные), 'other' (другое).
ALTER TABLE transactions ALTER COLUMN card_id DROP NOT NULL;
ALTER TABLE transactions ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'statement';
ALTER TABLE transactions ADD CONSTRAINT transactions_source_check
    CHECK (source IN ('statement', 'card', 'cash', 'other'));
