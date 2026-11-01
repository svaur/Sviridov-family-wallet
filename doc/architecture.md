# Architecture

## System shape

```
browser ──HTTP──> nginx :80 ──┬─ static SPA (React build in /usr/share/nginx/html)
                              └─ /api/*, /actuator/*, /swagger-ui*, /v3/api-docs
                                        │
                                        ▼
                              backend :8080 (Spring Boot)
                                        │ JDBC
                                        ▼
                              postgres :5432
```

- **One deployment unit**: `docker compose up --build` starts three containers
  (`frontend`, `backend`, `postgres`). No microservices, no queues.
- **Stateless backend** (except the login rate limiter): any instance can serve
  any request. Upload wizard state lives in the browser, not on the server.
- **No PDF storage**: the uploaded file is parsed and discarded; only statement
  metadata + parsed transactions are stored.

## Request flow (authenticated call)

1. Browser sends the session cookie (`JSESSIONID`, HttpOnly/SameSite=Strict).
2. `SecurityConfig` validates the cookie against the Spring Security context.
   `permitAll`: `/api/auth/login`, `/actuator/health`, `/actuator/info`,
   `/swagger-ui*`, `/v3/api-docs*`.
3. Controller receives `@AuthenticationPrincipal AppUser user`; passes
   `user.getId()` into the service.
4. Service loads data **scoped by `userId`** (never by raw id alone) and checks
   ownership before mutating.
5. Response DTO (record with static `from(...)`) is returned.
6. Errors are converted by `GlobalExceptionHandler` into the unified
   `ApiError` JSON: `{timestamp, status, error, message, path, fieldErrors[]}`.

## Import flow (PDF or screenshot) — stateless wizard

```
POST /api/statements/preview (multipart file, ?kind=statement|screenshot)
  → parser.parse(bytes)                    (PdfBoxStatementParser | ScreenshotParser)
  → per-row validation                     (XOR expense/income, positive amount, date range)
  → categorize each row                    (CategorizationEngine, rules ordered by priority)
  → StatementPreviewResponse (rows only, NO card created, NO draft stored)

[frontend: user edits/deletes/adds rows, assigns categories, applies rules]
POST /api/statements/categorize  (rows in → rows out, manual categories preserved)
POST /api/statements/confirm     (rows + statement metadata)
  → card find-or-create by last4
  → dedup by fingerprint
  → save statements + transactions
  → detect gaps
```

The browser is the source of truth for the draft. Closing the tab loses it —
that is accepted behaviour.

## Analytics flow

`GET /api/analytics?from=&to=&exclude=1,2,3` — server-side aggregation over
`transactions` filtered by `deleted_at IS NULL`, `is_transfer = false`,
`is_excluded = false`, date window and the excluded category ids
(`0` = "without category" sentinel).

## Security

- **Two security filter chains** in one app, resolved by path:
  - order 1 — `AdminSecurityConfig`: `/admin/**` and `/api/admin/**` require
    `ROLE_ADMIN` (permitAll only on `/api/admin/auth/login`).
  - order 2 — `SecurityConfig`: the wallet chain; it `denyAll()`s the admin
    paths and permits `/api/auth/login`, actuator and swagger.
  Both are explicit `ProviderManager`s (`userAuthenticationManager`,
  `adminAuthenticationManager`) so the two credential stores (`users` vs
  `admins`) never merge through a shared `UserDetailsService`. Each realm uses
  its own session-context attribute key (`SPRING_SECURITY_CONTEXT_KEY` vs
  `ADMIN_SECURITY_CONTEXT_KEY`), so logging into the admin site does not log out
  the wallet and vice versa; a logout removes only its own attribute.
- Session cookie, BCrypt password hashes, no self-registration.
- Login rate limiting: in-memory, 5 attempts / 5 min per `username|clientIp`
  (`LoginRateLimiter`), 429 on block; `X-Forwarded-For` aware. Wallet and admin
  logins use distinct keys.
- Deliberate per-process state, accepted for the single-instance deployment:
  the auth session (in-memory HTTP session, so a restart logs everyone out)
  and the rate-limit counters (reset on restart). Everything else — import
  drafts included — lives in the database or in the browser.
- Session invalidation via **epoch**: `users.session_epoch` is snapshotted into
  the session principal at login, and `SessionEpochFilter` (runs after Spring
  Security, one primary-key read per authenticated request) rejects a stale
  epoch with 401, removing only the wallet context so a coexisting admin
  session survives. A password change, ban, unban or forced logout bumps the
  epoch — stateless, no session registry. Login and logout are exempt.
- Forced password change: `MustChangePasswordFilter` (same ordering, no extra
  DB read — the flag is a login snapshot) returns 403 «Требуется смена пароля»
  on every wallet call except login/logout/`/api/me`/`PUT /api/me/password`
  while `users.must_change_password` is true.
- Banned user: `users.is_active=false` → `DisabledException` → 401 «Аккаунт
  заблокирован».
- CSRF disabled (same-site strict cookie + JSON-only API).
- Secrets via env (`.env` → docker-compose), never committed.

## Admin panel

A separate single-page app (`/admin` → `admin.html`) for managing the wallet:
list/create/ban/unban/reset-password/delete users, view the audit log and the
server status. It authenticates against the **`admins`** table through its own
chain (`/api/admin/**`), never against `users`, so an admin account cannot log
into the wallet. Every admin action writes an `audit_log` row via `AuditService`
(actor = the admin, target user id in `entity_id`, username captured in
`new_value` so the trail survives user deletion). Server status reads counts
from the repositories, `SELECT 1` for DB liveness, JVM/disk/uptime from the
runtime and the last import from the newest statement.

## Deployment

- `docker-compose.yml`: `postgres:16-alpine` (healthcheck), `backend`
  (multi-stage Maven build → `eclipse-temurin:21-jre-alpine`, includes
  `tesseract-ocr` + `tesseract-ocr-data-rus`), `frontend`
  (node:24-alpine build → nginx:1.27-alpine).
- nginx: SPA `try_files` fallback, `/api` proxy (12 MB body, 300 s read timeout
  for the OCR-heavy screenshot import), `/actuator`, `/swagger-ui`,
  `/v3/api-docs`.
- Flyway runs migrations automatically on backend start (`ddl-auto: validate`).
- Production checklist still open: TLS, JSON logs, hardening, backups (see
  `README.md` → "Production checklist").
