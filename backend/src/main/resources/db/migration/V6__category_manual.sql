-- Manual category assignment flag: a category assigned by hand is protected
-- from being overwritten by rule auto-application or the "apply rules" button.
ALTER TABLE transactions ADD COLUMN category_manual BOOLEAN NOT NULL DEFAULT false;
