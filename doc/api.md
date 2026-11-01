# REST API

All endpoints require an authenticated session cookie unless noted. Errors are
always the unified `ApiError` shape:

```json
{ "timestamp": "…", "status": 400, "error": "Validation Error",
  "message": "Ошибка валидации", "path": "/api/…",
  "fieldErrors": [ { "field": "username", "message": "Логин обязателен" } ] }
```

## Auth (`/api/auth`) — public except `/logout`

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/login` | `{username, password}` | `UserResponse`; 401 on bad creds; 429 when rate-limited |
| POST | `/api/auth/logout` | — | 204 |

Rate limit: 5 failures / 5 min per `username|ip` → 429 "Слишком много попыток".

## Current user (`/api/me`)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/me` | — | `UserResponse` (`id, username, firstName, lastName, mustChangePassword`) |
| PUT | `/api/me/profile` | `{firstName?, lastName?}` | `UserResponse` |
| PUT | `/api/me/password` | `{currentPassword, newPassword}` | 204 |

`firstName`/`lastName` are display data only (the login never changes). They are
trimmed, inner spaces are collapsed and a blank value is stored as `null`, so
the sidebar can fall back to the login with a single check.

`newPassword` is 8–72 characters. The current password is verified first — a
wrong one returns **400** "Неверный текущий пароль" and counts towards a
5-failures/5-min limit per user (429); the endpoint is never an oracle for the
new-password rules. Changing the password bumps `users.session_epoch`, so
**every** session, including the one that made the call, gets
`401 "Сессия завершена. Войдите снова."` on its next request.

When an admin resets a user's password (or a user is created by an admin),
`mustChangePassword` is `true`: every wallet call except login/logout/`/api/me`
and `PUT /api/me/password` returns **403** "Требуется смена пароля" until the
user picks a new password through the forced change-password screen.

## Cards (`/api/cards`)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/cards` | — | `CardResponse[]` (`id, cardNumber, displayName, last4, currency, archived, createdAt`) |
| POST | `/api/cards` | `{cardNumber, displayName}` | 201 `CardResponse` |
| PUT | `/api/cards/{id}` | `{cardNumber?, displayName}` | `CardResponse` |
| DELETE | `/api/cards/{id}` | — | 204 |

Cards are usually created automatically on import; the "Карты" page also allows
manual add/edit/delete. `cardNumber` is required on create (at least 4 digits —
`last4` is the matching key) and optional on update: the statements page's inline
rename sends only `displayName`. A number whose last4 already belongs to another
of the user's cards is rejected (400). `DELETE` is a hard delete that cascades to
the card's statements, transactions and statement gaps, so re-importing the same
statement afterwards works.

## Categories (`/api/categories`)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/categories` | — | `CategoryResponse[]` (`id, parentId, name, icon`) |
| POST | `/api/categories` | `{name, icon?, parentId?}` | 201 `CategoryResponse` |
| PUT | `/api/categories/{id}` | `{name, icon?, parentId?}` | `CategoryResponse` |
| DELETE | `/api/categories/{id}` | — | 204 |

`icon` must be one of the 23 allowed emoji (`CategoryService.ALLOWED_ICONS`);
unknown → 400 "Недопустимый значок категории".

## Rules (`/api/rules`)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/rules` | — | `RuleResponse[]` (`id, name, params, categoryId, priority, autoLearned`) |
| POST | `/api/rules` | `{name?, params, categoryId, priority?}` | 201 `RuleResponse` |
| PUT | `/api/rules/{id}` | same | `RuleResponse` |
| DELETE | `/api/rules/{id}` | — | 204 |
| POST | `/api/rules/apply` | — | `{recategorized: N}` |

`params` keys: `operation_contains`, `operation_regex`, `purpose_contains`,
`purpose_regex`, `min`, `max`, `day_from`, `day_to`. Unknown key / empty params
/ invalid regex → 400. Creating/updating/deleting a rule re-categorizes all
non-manual transactions.

## Transactions (`/api/transactions`)

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/transactions` | — | `TransactionResponse[]` |
| POST | `/api/transactions` | `TransactionRequest` | 201 `TransactionResponse` |
| DELETE | `/api/transactions/{id}` | — | 204 (soft delete) |
| PUT | `/api/transactions/{id}` | `{operation?, amount, date}` | 200 (edit; the fingerprint is **not** recomputed) |
| POST | `/api/transactions/{id}/category` | `{categoryId}` | 200 (sets `category_manual`) |
| PUT | `/api/transactions/{id}/comment` | `{comment}` | 200 (`null`/blank clears) |

`TransactionRequest`: `{source: "card"|"cash"|"other", cardId?, date, type:
"debit"|"credit", amount>0, purpose?, categoryId?}`.

`TransactionResponse`: `id, date, operation, purpose, amount, type, categoryId,
categoryName, comment, source, cardId, isTransfer`.

## Statements (`/api/statements`)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/statements/preview?kind=statement\|screenshot` | multipart `file` | `StatementPreviewResponse` (rows only, no draft id) |
| POST | `/api/statements/categorize` | `PreviewTransaction[]` | `PreviewTransaction[]` |
| POST | `/api/statements/confirm` | `ConfirmRequest` | `StatementImportResponse` |
| GET | `/api/statements` | — | `StatementResponse[]` |
| DELETE | `/api/statements/{id}` | — | `{deleted: N}` — **hard** delete of the statement's transactions |

`StatementPreviewResponse`: `periodFrom, periodTo, cardNumber, contract,
account, rowCountRaw, validCount, warnings[], unrecognizedRows[],
parserVersion, transactions[{rowNumber, date, operation, purpose, amount, type,
categoryId, categoryName, categoryManual}]`.

`ConfirmRequest`: `cardNumber, parserVersion, periodFrom, periodTo, contract,
account, rowCountRaw, warnings[], unrecognizedRows[], transactions[]`.
`cardNumber` may be empty when the parser could not extract it — the user types
it in the wizard.

`StatementImportResponse`: `statementId, status, rowCountRaw, importedCount,
skippedDuplicates, warnings[], unrecognizedRows[]`. `statementId` is `null` when
every row was a duplicate — no statement row is created in that case.

Upload limit: 10 MB (`spring.servlet.multipart`); 413 beyond that.

## Statement gaps (`/api/statement-gaps`)

| Method | Path | Response |
|---|---|---|
| GET | `/api/statement-gaps` | `StatementGapResponse[]` (`id, cardId, gapFrom, gapTo, detectedAt`) |

## Analytics (`/api/analytics`)

`GET /api/analytics?from=YYYY-MM-DD&to=YYYY-MM-DD&exclude=1,2,3`

- `from`/`to` optional; default = trailing 12 months, `from <= to` enforced.
- `exclude` = category ids to hide (`0` = uncategorized).

Response: `{from, to, income, expense, balance, monthly[{month, income,
expense}], categories[{categoryId, categoryName, amount}]}`.
Transfers (`is_transfer`), excluded transactions (`is_excluded`) and rows in
the "исключено" category are never counted.

## Admin (`/api/admin/*`)

A **separate security realm** served by the admin SPA at `/admin`. Admin and
wallet sessions live in the same cookie but under different Spring Security
context keys, so an admin and a wallet user can be logged in side by side;
logging out of one never touches the other. Admin endpoints require `ROLE_ADMIN`;
an admin session is denied on wallet endpoints and a wallet session is denied on
admin endpoints.

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/admin/auth/login` | `{username, password}` | `AdminResponse`; 401 on bad creds; 429 rate-limited |
| POST | `/api/admin/auth/logout` | — | 204 |
| GET | `/api/admin/auth/me` | — | `AdminResponse` (`id, username, mustChangePassword`) |
| GET | `/api/admin/users` | — | `UserAdminResponse[]` |
| POST | `/api/admin/users` | `{username, password, firstName?, lastName?}` | 201 `UserAdminResponse` |
| POST | `/api/admin/users/{id}/ban` | — | 204 (next login → 401 «Аккаунт заблокирован») |
| POST | `/api/admin/users/{id}/unban` | — | 204 |
| POST | `/api/admin/users/{id}/reset-password` | — | `{tempPassword}` (user must change it on next login) |
| POST | `/api/admin/users/{id}/logout` | — | 204 (kills the user's existing sessions) |
| DELETE | `/api/admin/users/{id}` | — | 204 (hard delete, cascades to the user's data) |
| GET | `/api/admin/audit?page=0&size=50` | — | `Page<AuditResponse>` (newest first) |
| GET | `/api/admin/status` | — | `ServerStatusResponse` |

`UserAdminResponse`: `id, username, firstName, lastName, active,
mustChangePassword, lastLoginAt, createdAt`.
`AuditResponse`: `id, at, actorKind, actorId, entity, entityId, action,
username, ip`. Every admin action writes one audit row (`create`/`ban`/`unban`/
`reset_password`/`force_logout`/`delete` on users, `login` on the admin itself).
`ServerStatusResponse`: `version, uptimeSeconds, dbStatus, userCount,
adminCount, transactionCount, statementCount, cardCount, categoryCount,
ruleCount, lastImportAt, jvmMaxBytes, jvmUsedBytes, diskTotalBytes,
diskUsableBytes`.

## Misc

| Method | Path | Response |
|---|---|---|
| GET | `/api` | `{name, version, time}` (health/identification) |

Swagger UI: `/swagger-ui.html`; OpenAPI JSON: `/v3/api-docs`.
