# Testing

Two layers, deliberately small: fast unit tests for logic, one integration
suite for the client paths that a user actually walks through.

## Commands

```bash
cd backend
mvn test        # unit tests only — no Docker, no Spring context
mvn verify      # unit tests + *IT (Testcontainers PostgreSQL) + failsafe
```

`mvn test` is the day-to-day loop (~5 s). `mvn verify` additionally starts a
throwaway `postgres:16-alpine` container, runs Flyway V1–V14 against it and drives
the app through MockMvc (~20–40 s including container start).

Two environment facts `mvn verify` depends on:

- **Docker socket.** On Docker Desktop for macOS the socket is
  `~/.docker/run/docker.sock`, not `/var/run/docker.sock` (which Testcontainers
  probes first). Export `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`.
  Linux hosts with the default socket need nothing.
- **Docker API version.** docker-java (inside Testcontainers 1.19.x) speaks API
  `1.32` by default and Docker Engine 25+ answers that with `400`, which
  Testcontainers reports as `Could not find a valid Docker environment`. The
  failsafe plugin therefore passes `api.version` (property
  `docker.api.version`, default `1.44`); on an older engine run
  `mvn verify -Ddocker.api.version=1.43`.

Frontend: `cd frontend && pnpm build` (TypeScript project references + Vite). It
has no test runner — the SPA is checked by building it and by the backend IT
asserting the JSON it consumes.

## Unit tests (`mvn test`)

Plain JUnit 5 + Mockito, no Spring context: services are constructed by hand with
mocked repositories. They cover the pure logic that is expensive to reach
through HTTP:

| Area | Class | What it pins |
|---|---|---|
| PDF parsing | `PdfBoxStatementParserTest` | header card/period extraction, amount column detection, multi-page grouping |
| Screenshot OCR | `ScreenshotParserTest` | row clustering, both-amounts rows, zero-amount rows, TSV fixtures (no Tesseract needed) |
| Rules matching | `CategorizationEngineTest` | implicit AND, priority order, `hasKnownCondition` guard |
| Rule validation | `RuleServiceTest` | unknown keys, empty params, regex compile, `min`/`max`, `day_from`/`day_to`, canonicalisation, re-categorisation trigger |
| Category validation | `CategoryServiceTest` | emoji allow-list, lazy "исключено" seeding, system category protection, flush-before-re-categorise |
| Transactions | `TransactionServiceTest` | dedup, fingerprint stability, soft-delete tombstone, edit |
| Profile & password | `UserServiceTest` | name trimming/blank → null, wrong current password + rate-limit failure, same-password rejection, hash replacement and epoch bump, blocked after 5 failures |
| Admin auth | `AdminServiceTest` | `get` → response, unknown → NotFound, `recordLogin` stamps `lastLoginAt` |
| Admin user management | `AdminUserServiceTest` | create/ban/unban/reset-password/force-logout/delete + `audit.record(...)` on mutating actions |
| Audit | `AuditServiceTest` | username captured in `new_value`, list maps + tolerates unparsable JSON |
| Server status | `ServerStatusServiceTest` | counts + `dbStatus` ok/error |
| Statements | `StatementServiceTest` | import counts, duplicate skipping, partial status |
| Cards | `CardServiceTest` | number → `last4`, duplicate numbers, rename, delete |
| Analytics | `AnalyticsServiceTest` | totals, exclusions, monthly buckets |
| Gaps | `StatementGapServiceTest`, `StatementGapDetectorTest` | threshold, open-gap opening |
| Security | `LoginRateLimiterTest` | 5/5 min window, key expiry, sweep |
| Misc | `TextNormalizerTest`, `PasswordEncoderTest`, `RootControllerTest` | — |

One test is skipped when `tesseract` is not installed locally (end-to-end
screenshot OCR via the CLI); the Docker image always has it.

## Integration tests (`mvn verify`)

`src/test/java/ru/sviridov/wallet/it/WalletFlowIT.java` — `@SpringBootTest` +
`@AutoConfigureMockMvc` on a Testcontainers PostgreSQL. This is the only layer
that runs the real security filter chain, the real controllers/JSON contract,
the real repositories (all JPQL/derived queries) and the real Flyway schema.

Eight client paths in `WalletFlowIT`:

| Test | Path it walks |
|---|---|
| `loginLogoutAndAccessControl` | 401 body, wrong password, login, `/me`, logout kills the session |
| `cardsCrud` | create → list → rename → duplicate 400 → validation 400 → delete 204 |
| `pdfImportWithRuleCategorization` | upload PDF → preview counts → create category + rule → re-categorize preview → confirm → persisted totals |
| `duplicatesAreSkippedAndDeletingAStatementRemovesItsData` | re-import skips 12/12, hard delete of a statement returns its rows, the same file imports again |
| `analyticsTotalsAndExclusions` | totals, `exclude=0` sentinel, «исключено» category drops rows |
| `manualTransactionLifecycle` | manual create, edit, comment, invalid edit 400, delete |
| `profileAndPasswordChange` | save name → `/api/me`; short/new-password validation; wrong current password 400; change 204 → own session 401; old password rejected, new one logs in |
| `forcedPasswordChangeAfterAdminReset` | admin resets the password → login with the temp password → wallet call 403 «Требуется смена пароля» → change 204 → own session 401 → re-login with the new password works |

Two more IT classes cover the admin realm on the same Testcontainers context:
`AdminAuthIT` (admin login/logout/me, wallet↔admin isolation, wallet + admin
sessions coexisting in one cookie) and `AdminUserIT` (user CRUD, ban/unban,
reset-password, force-logout, and the audit/status path). Admin tests isolate
themselves with `userRepository.deleteAll(); adminRepository.deleteAll();` in
`@BeforeEach` because the admin panel sees a **global** namespace.

`it/Api.java` is a small package-private helper around MockMvc (session cookie,
JSON verbs, multipart upload) so each test reads as a sequence of API calls.

**Isolation**: every test creates its own user (`it-<uuid>`) and every query is
scoped by `user_id`, so tests never see each other's rows and need no
truncation between runs. Create data through the API where possible; the only
repository write is the user itself (there is no public registration).

**Why MockMvc, not a random port**: no port allocation, no client-side cookie
jar, no waiting for Tomcat; the filter chain and servlet layer are still real.
The trade-off is that the HTTP server itself is not exercised — acceptable for
a 1–3 user app.

**Fixtures** live in `backend/src/test/resources/` and are sanitized (fictional
card number, fictional merchants): `TEST_PDF.pdf` (12 operations, 2026-09-01…09-30,
income 135 935,00 / expense 23 589,40) and `screenshot_test.png`. Never add a
real statement or screenshot — see the security rules in `AGENTS.md`.

**Do not trip the login rate limiter**: 5 failed logins per `username|ip` in 5
minutes return 429. The access-control test makes exactly one wrong-password
attempt.

## Adding a new integration path

1. Add a verb to `it/Api.java` if it is missing (`getRaw`, `postRaw`, `putRaw`,
   `deleteRaw`, `upload` already return `MvcResult` when you need status codes).
2. Add a `@Test` to `WalletFlowIT`; it inherits the logged-in user from
   `@BeforeEach`.
3. Assert on status codes and on the JSON body (the contract lives in
   `doc/api.md`), not on repository state.
4. Keep it in the same class unless it needs a different container or context.
