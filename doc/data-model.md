# Data model

PostgreSQL, managed by Flyway. `spring.jpa.hibernate.ddl-auto=validate` — the
schema is owned by migrations, not by Hibernate.

All timestamps are `TIMESTAMPTZ` (UTC in DB, rendered Europe/Moscow in the UI).
Every user-owned table is scoped by `user_id`; services always check ownership.

## Migrations (V1–V14)

| # | File | What it does |
|---|---|---|
| V1 | `V1__init.sql` | pipeline check (`SELECT 1`) |
| V2 | `V2__full_schema.sql` | creates **all** tables upfront |
| V3 | `V3__flexible_rules.sql` | rules: drop `type`, transform `params` to flat keys |
| V4 | `V4__rule_name.sql` | `rules.name VARCHAR(120)` |
| V5 | `V5__transaction_source.sql` | `transactions.card_id` nullable, `source` column + check |
| V6 | `V6__category_manual.sql` | `transactions.category_manual BOOLEAN` |
| V7 | `V7__drop_merchant_normalized.sql` | drops `transactions.merchant_normalized` |
| V8 | `V8__card_auto.sql` | `cards.card_number` added, `cards.bank` dropped |
| V9 | `V9__category_icon.sql` | `categories.icon VARCHAR(8) DEFAULT '📦'` |
| V10 | `V10__screenshot_source.sql` | check allows `'screenshot'` |
| V11 | `V11__excluded_category.sql` | `categories.is_excluded` + seed one "исключено" per user |
| V12 | `V12__user_profile.sql` | `users.first_name`, `last_name`, `password_changed_at` |
| V13 | `V13__admins_and_user_management.sql` | `admins` table; `users.must_change_password`, `is_active`, `last_login_at`; rename `password_changed_at` → `session_epoch` |
| V14 | `V14__audit_log_rework.sql` | `audit_log.user_id` nullable + `ON DELETE SET NULL`; `actor_kind`/`actor_id`/`ip`; index on `at` |

**Rule when you change the schema:** add a new `Vn__*.sql`, never edit an
applied migration (Flyway validates checksums).

## Tables

### users
`id`, `username` (unique), `password_hash` (BCrypt), `first_name`, `last_name`,
`session_epoch`, `must_change_password`, `is_active` (default true),
`last_login_at`, `created_at`.
No self-registration — users are created by `AdminUserBootstrap`
(`BOOTSTRAP_USERNAME` / `BOOTSTRAP_PASSWORD`), by the admin panel, or manually in SQL.

`first_name`/`last_name` are optional display data used by the sidebar (the login
itself is immutable). `session_epoch` is an **epoch**, not an audit field: it is
snapshotted into the session at login and compared on every request, so a password
change, ban, unban or forced logout invalidates all sessions without a server-side
session registry. `must_change_password` (set on creation and on an admin password
reset) gates the wallet to the change-password screen until a new password is
chosen. `is_active=false` = banned — the next login returns 401 «Аккаунт
заблокирован».

### admins
`id`, `username` (unique), `password_hash` (BCrypt), `must_change_password`
(default true on bootstrap), `is_active` (default true), `last_login_at`,
`created_at`.
Admins are a **separate realm** — an admin cannot log into the wallet and a
wallet user cannot log into the admin site. Created by `AdminBootstrap`
(`ADMIN_USERNAME` / `ADMIN_PASSWORD`, `must_change_password=true`).

### cards
`id`, `user_id`, `display_name`, `card_number VARCHAR(20)`, `last4`,
`currency` (default RUB), `archived`, `created_at`.

Cards are **auto-created** on the first import of a statement/screenshot: the
parser extracts the masked number, `CardService.findOrCreateByNumber` matches by
**last 4 digits** (`findFirstByUserIdAndLast4OrderByIdAsc`) and creates one if
absent. A PDF card (`2202XXXXXXXX4141`) and a screenshot card (`••••4141`)
resolve to the same row. last4 is not unique (re-imports can leave duplicates),
so the lookup uses `findFirst…` and the earliest row wins.

### categories
`id`, `user_id`, `parent_id` (self-FK, nullable), `name`, `icon` (emoji),
`is_excluded` (default false).
The only seeded category is the system **"исключено"** (`is_excluded = true`,
icon 🚫), created lazily on first list; assigning a transaction to it (by rule
or by hand) removes it from analytics. It cannot be deleted.

### rules
`id`, `user_id`, `name`, `params JSONB`, `category_id`, `priority` (default 0),
`auto_learned`.

`params` is a flat object of optional keys; a rule matches iff **all** present
conditions match:

```json
{ "operation_contains": "пятёроч", "purpose_contains": "продукт",
  "min": 500, "max": 10000, "day_from": 15, "day_to": 20 }
```

Unknown keys are rejected by `RuleService`. Empty params are rejected.
User rules default to priority `100` (higher = checked first).

### statements
`id`, `card_id`, `uploaded_at`, `period_from`, `period_to`, `parser_version`,
`status` (`OK` / `PARTIAL`), `txn_count` (rows this import actually stored),
`row_count_raw` (rows the parser saw), `card_number_extracted`, `contract`,
`account`. A fully duplicate re-import stores no statement row.

The PDF/image itself is **never stored**. "Delete statement" hard-deletes its
transactions (so a re-upload is not blocked by the dedup tombstones).

### transactions
`id`, `user_id`, `card_id` (nullable for cash/other), `statement_id` (nullable),
`row_number`, `date`, `operation`, `purpose`, `amount NUMERIC(15,2)`,
`type` (`debit`/`credit`), `category_id` (nullable = uncategorized),
`is_transfer`, `is_excluded`, `comment`, `source`
(`statement|screenshot|card|cash|other`), `category_manual`, `fingerprint`,
`deleted_at`, `created_at`.

Invariants:
- `type=debit` ⇒ amount is an expense; `type=credit` ⇒ income.
- `deleted_at != null` = soft-deleted (tombstone kept so re-import cannot
  resurrect a transaction the user deleted).
- `category_manual=true` ⇒ re-categorization must not touch `category_id`.
- `fingerprint` = SHA-256 of `userId|cardId|date|amount|type|purposeNorm`
  (purpose lowercased/collapsed, NOT prefix-stripped). Null for manual rows.

### statement_gaps
`id`, `card_id`, `gap_from`, `gap_to`, `detected_at`.
A gap is a pause between consecutive statements of one card longer than
`app.gap.days` (default 7). Idempotent via `(card_id, gap_from, gap_to)`.

### audit_log
`id`, `user_id` (nullable, `ON DELETE SET NULL`), `entity`, `entity_id`,
`action`, `old_value`/`new_value` (JSONB), `at`, `actor_kind`, `actor_id`, `ip`.
Filled **only** by admin actions (`AuditService`): `user_id` is always `null`
for admin entries (the actor is the admin, kept in `actor_id`); the target user
id is stored durably in `entity_id`, and the target username is captured in
`new_value = {"username":"…"}` so the trail survives user deletion.

### notifications
Created upfront, **not used yet** (backlog: in-app notifications).
