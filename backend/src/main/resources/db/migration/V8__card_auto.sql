-- Cards are now created automatically from the statement's extracted card number.
-- The full masked number becomes the card's natural identity; `bank` is dropped.
ALTER TABLE cards ADD COLUMN card_number VARCHAR(20);
ALTER TABLE cards DROP COLUMN bank;
