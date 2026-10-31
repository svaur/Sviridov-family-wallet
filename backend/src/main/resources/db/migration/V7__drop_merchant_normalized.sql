-- Rules now match the raw «Операция» (operation) and «Назначение платежа»
-- (purpose) columns case-insensitively; automatic merchant normalisation has
-- been removed, so the merchant_normalized column is no longer needed.
ALTER TABLE transactions DROP COLUMN merchant_normalized;
