# REST API

All endpoints require an authenticated session cookie unless noted. Errors are
always the unified `ApiError` shape:

```json
{ "timestamp": "…", "status": 400, "error": "Validation Error",
  "message": "Ошибка валидации", "path": "/api/…",
  "fieldErrors": [ { "field": "username", "message": "Логин обязателен" } ] }
```

## Auth (`/api/auth`) — public except `/me`, `/logout`

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/login` | `{username, password}` | `UserResponse`; 401 on bad creds; 429 when rate-limited |
| GET | `/api/auth/me` | — | `UserResponse` |
| POST | `/api/auth/logout` | — | 204 |

Rate limit: 5 failures / 5 min per `username|ip` → 429 "Слишком много попыток".

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

## Misc

| Method | Path | Response |
|---|---|---|
| GET | `/api` | `{name, version, time}` (health/identification) |

Swagger UI: `/swagger-ui.html`; OpenAPI JSON: `/v3/api-docs`.
