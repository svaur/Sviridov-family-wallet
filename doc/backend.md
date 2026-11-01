# Backend (Java 21 / Spring Boot 3.3.5)

Root package: `ru.sviridov.wallet`. One Spring Boot application, layered:
`web` (controllers + DTOs) → `service` (business logic) → `domain` (JPA
entities + repositories) → `parser` (file parsing) → `security` / `config`.

## Package map

### `domain/` — entities + repositories

| Class | Notes |
|---|---|
| `User` | `id`, `username`, `passwordHash`, `firstName`, `lastName`, `sessionEpoch`, `mustChangePassword`, `isActive`, `lastLoginAt`, `createdAt` |
| `Admin` | separate admin realm: `username`, `passwordHash`, `mustChangePassword`, `isActive`, `lastLoginAt`, `createdAt` |
| `AuditLog` | admin action trail: `entity`/`entityId`/`action`/`oldValue`/`newValue` (JSON), `actorKind`/`actorId`/`ip`, `at` |
| `Card` | `cardNumber`, `last4`, `displayName`, `currency`; created automatically |
| `Category` | `name`, `icon` (emoji), `parentId` |
| `Rule` | `params` as `String` JSONB (`@JdbcTypeCode(SqlTypes.JSON)`), `priority` |
| `Statement` | metadata only — the file is not stored |
| `Transaction` | the central entity; `source`, `categoryManual`, `fingerprint`, `deletedAt` |
| `StatementGap` | gap between two consecutive statements of a card |

Repositories are Spring Data JPA. Two queries are worth knowing:

- `TransactionRepository.findExistingFingerprints(Collection<String>)` —
  **includes soft-deleted** rows (tombstone).
- `TransactionRepository.findActiveByUserId(Long)` — `deletedAt IS NULL`,
  ordered `date DESC, id DESC` (used by feed + analytics + re-categorize).

### `parser/` — `StatementParser` implementations

- `StatementParser` — `ParsedStatement parse(byte[])`, `default version()`.
- `ParsedStatement` / `ParsedRow` / `OcrLine` — value records.
- `PdfBoxStatementParser` — PDFBox text/position extraction.
- `ScreenshotParser` — Tesseract OCR + geometric blank-space segmentation
  (see [domain-logic.md](domain-logic.md) §5.2).

### `service/` — business logic

| Service | Responsibility |
|---|---|
| `CardService` | list/get/create/update/delete (manual CRUD), `findOrCreateByNumber` (match by last4), `findByLast4` (read-only overlap check) |
| `CategoryService` | CRUD; `delete` removes rules (FK cascade) + re-categorizes |
| `RuleService` | CRUD; validates/normalizes `params`; re-categorizes on change |
| `CategorizationEngine` | flat-AND rule matching → category id or null |
| `TextNormalizer` | lowercase + collapse whitespace (no prefix stripping) |
| `TransactionService` | feed list, create (manual), delete (soft), assign category, comment, `reCategorizeAll` |
| `StatementService` | preview / categorize / confirm / list / delete (hard) |
| `StatementGapService` | `detectGaps(cardId)` after confirm/delete |
| `AnalyticsService` | KPI + monthly + category aggregation with excludes |
| `LoginRateLimiter` | in-memory 5 attempts / 5 min per `username|ip`; expired keys are dropped and the map is swept past `MAX_KEYS` |
| `UserService` | profile get/update; password change (verifies current, bumps epoch, rate-limited) |
| `AdminService` | `get` + `recordLogin` for the admin realm |
| `AdminUserService` | admin CRUD + ban/unban/reset-password/force-logout/delete over users; writes an audit row per action |
| `AuditService` | `record(entity, entityId, action, username, actorId, ip)` + paged `list` (extracts username from `new_value`) |
| `ServerStatusService` | counts, DB liveness (`SELECT 1`), uptime, JVM/disk, last import |

### `web/` — controllers + DTOs + errors

- `AuthController` (`/api/auth`), `MeController` (`/api/me`), `CardController`
  (`/api/cards`), `CategoryController` (`/api/categories`), `RuleController`
  (`/api/rules`), `TransactionController` (`/api/transactions`),
  `StatementController` (`/api/statements`), `StatementGapController`
  (`/api/statement-gaps`), `AnalyticsController` (`/api/analytics`),
  `RootController` (`/api`).
- Admin realm: `AdminAuthController` (`/api/admin/auth`), `AdminUserController`
  (`/api/admin/users`), `AdminAuditController` (`/api/admin/audit`),
  `AdminStatusController` (`/api/admin/status`).
- DTOs are immutable `record`s with a static `from(entity)` where useful.
- Errors: `ApiError` record + `GlobalExceptionHandler`
  (`MethodArgumentNotValidException` → 400 with `fieldErrors`,
  `BadCredentialsException` → 401, `NotFoundException` → 404,
  `BadRequestException` → 400, `TooManyRequestsException` → 429,
  `MaxUploadSizeExceededException` → 413, fallback → 500).

### `security/`

- `SecurityConfig` — wallet chain (`@Order(2)`, `denyAll` on `/admin/**`), JSON 401/403, `userAuthenticationManager`.
- `AdminSecurityConfig` — admin chain (`@Order(1)`, `/admin/**` + `/api/admin/**`, `ROLE_ADMIN`), `ADMIN_SECURITY_CONTEXT_KEY`, `adminAuthenticationManager`.
- `AppUser` / `AppAdmin` — `UserDetails` with `id`; `AppUserDetailsService` / `AdminDetailsService` load from `users` / `admins`.
- `SessionEpochFilter` — rejects a stale `sessionEpoch` (401), removing only the wallet context so a coexisting admin session survives.
- `MustChangePasswordFilter` — 403 «Требуется смена пароля» while `must_change_password` is true.

### `config/`

- `AdminUserBootstrap` — `CommandLineRunner` creating the bootstrap wallet user
  when `BOOTSTRAP_USERNAME`/`BOOTSTRAP_PASSWORD` are set (password ≥ 8 chars).
- `AdminBootstrap` — `CommandLineRunner` creating the bootstrap admin when
  `ADMIN_USERNAME`/`ADMIN_PASSWORD` are set (password ≥ 8 chars,
  `must_change_password=true`).

## Conventions

- Explicit imports (no wildcards).
- Controllers return DTOs, never entities.
- Ownership check is `service.get(userId, id)` → `findByIdAndUserId(...)
  .orElseThrow(NotFoundException)`.
- `@Transactional` on every service method that writes; read-only for reads
  where useful.
- Money is `BigDecimal` (`NUMERIC(15,2)`); never `double`.
- Date parsing/formatting happens in the frontend (`Europe/Moscow`), the backend
  works with `LocalDate`.

## Configuration (`application.yml`)

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (defaults for local dev).
- `COOKIE_SECURE` (false locally, true behind TLS).
- `BOOTSTRAP_USERNAME` / `BOOTSTRAP_PASSWORD`; `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
- `info.app.version` (default 0.1.0, shown in the server status).
- `app.gap.days` (default 7).
- `spring.servlet.multipart.max-file-size` = 10 MB.
- `springdoc.swagger-ui.path` = `/swagger-ui.html`.

## Tests

`mvn test` — 148 tests (1 skipped: the end-to-end OCR test needs a local
Tesseract); `mvn verify` adds 17 integration tests on Testcontainers. Unit tests
use plain JUnit 5 + AssertJ + Mockito; there is no Spring context in most tests
(services are constructed manually with mocks).
