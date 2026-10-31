-- Stage 6: category icon. Every category gets an emoji icon picked from a fixed list.
ALTER TABLE categories ADD COLUMN icon VARCHAR(8) NOT NULL DEFAULT '📦';
