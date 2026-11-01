# Sviridov Family Wallet

Self-hosted personal/family finance tracker built on PDF bank statements.

**Stack:** Java 21 · Spring Boot 3 (web, data-jpa, validation, security, actuator) · Flyway · PostgreSQL ·
Apache PDFBox · springdoc-openapi (Swagger UI) · React + TypeScript + Vite · React Router · Recharts · nginx.

## Repository layout

```
backend/            Spring Boot 3 single-module app (Maven)
frontend/           React + TypeScript + Vite SPA (built and served by nginx)
docker-compose.yml  postgres + backend + frontend (nginx)
.env.example        env template (copy to .env)
```

## Prerequisites

- Docker + Docker Compose — all you need for the containerised run.
- Local development only: Java 21, Maven 3.9+, Node.js 22.5+ and pnpm 11.
  (pnpm 11 uses Node's built-in `node:sqlite`, which requires Node ≥ 22.5.)
- Screenshot import uses Tesseract OCR — installed inside the Docker image
  (`apk add tesseract-ocr tesseract-ocr-data-rus`); for a non-Docker local run, install
  `tesseract-ocr` with the `rus` language pack yourself.

## Build & run (Docker)

1. `cp .env.example .env` and set real passwords (Postgres + bootstrap user).
2. `docker compose up --build`
   - builds and starts three containers:
     - **frontend** (nginx) on `:80` — serves the SPA and proxies `/api`, `/actuator`,
       `/swagger-ui`, `/v3/api-docs` to the backend
     - **backend** (Spring Boot) on `:8080`
     - **postgres** on `:5432`
   - App: `http://localhost` · Swagger UI: `http://localhost/swagger-ui.html`
   - Health: `GET http://localhost:8080/actuator/health`
   - On first start, Flyway creates the full schema (V1–V10); if `BOOTSTRAP_USERNAME` /
     `BOOTSTRAP_PASSWORD` are set, the first user is created automatically.
3. Rebuild after changes: `docker compose up --build --force-recreate`.
4. Stop: `docker compose down` (add `-v` to also wipe the database volume).

## Run (development, hot reload)

- Backend: `cd backend && mvn spring-boot:run` (needs a running Postgres).
- Frontend:
  ```bash
  cd frontend
  pnpm install
  pnpm dev          # http://localhost:5173, proxies /api and /actuator to :8080
  ```

## Auth

- Cookie session (HttpOnly, SameSite=Strict; `Secure` via `COOKIE_SECURE=true` behind TLS).
- Passwords hashed with BCrypt. Users are created manually — there is no self-registration.
- Create a user without restart (SQL):
  ```sql
  INSERT INTO users (username, password_hash)
  VALUES ('family', '$2a$10$<bcrypt-hash>');  -- generate with htpasswd -bnBC 10 "" <password> | tr -d ':\n'
  ```
- Endpoints: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/me`, `PUT /api/me/profile`, `PUT /api/me/password`.

## API

All endpoints require a cookie session (except login/health/info). All errors share one shape:
`{ timestamp, status, error, message, path, fieldErrors[] }`.

Cards (auto-created from statement uploads):
- `GET /api/cards` — list my cards
- `PUT /api/cards/{id}` — rename (`{ displayName }`)

Categories:
- `GET /api/categories` · `POST /api/categories` · `PUT /api/categories/{id}` · `DELETE /api/categories/{id}`

Rules (flat optional conditions, implicit AND — `operation_contains`/`operation_regex`,
`purpose_contains`/`purpose_regex`, `min`/`max`, `day_from`/`day_to`):
- `GET /api/rules` · `POST /api/rules` · `PUT /api/rules/{id}` · `DELETE /api/rules/{id}`
- `POST /api/rules/apply` — re-apply rules to all transactions (skips manually-categorised)

Statements (3-step upload wizard, ≤10 MB, PDF not stored):
- `POST /api/statements/preview` — multipart `file` + optional `kind` (`statement` default | `screenshot` for a bank-feed screenshot image); parse + validate + categorise → draft preview
- `POST /api/statements/preview/{draftId}/recategorize` — re-apply rules to the draft
- `DELETE /api/statements/preview/{draftId}/transactions/{rowNumber}` — remove a row from the draft
- `PUT /api/statements/preview/{draftId}/transactions/{rowNumber}` — edit a draft row (`operation`, `amount`, `date`)
- `POST /api/statements/preview/{draftId}/transactions` — add a row to a screenshot draft (`operation`, `amount`, `type`, `date`)
- `POST /api/statements/preview/{draftId}/transactions/{rowNumber}/category` — manual category in the draft
- `POST /api/statements/preview/{draftId}/confirm?cardNumber=…` — import (dedup, save statement + transactions); `cardNumber` is used only when the card number could not be extracted from the document
- `GET /api/statements` — upload history (all cards)
- `DELETE /api/statements/{id}` — delete a statement together with its imported transactions (hard delete, so a re-upload imports them fresh)
- `GET /api/statement-gaps` — detected gaps between statements

Transactions:
- `GET /api/transactions` — list my transactions (`source`: `statement` | `screenshot` | `card` | `cash` | `other`)
- `POST /api/transactions` — manual entry (`source` = card|cash|other)
- `DELETE /api/transactions/{id}` — soft-delete
- `POST /api/transactions/{id}/category` — assign category manually
- `POST /api/transactions/{id}/transfer` — mark as transfer (excluded from stats)
- `PUT /api/transactions/{id}/comment` — set/clear the manual comment

Analytics:
- `GET /api/analytics?from=&to=&exclude=1,2,3` — KPI + monthly + per-category (excludes transfers & excluded)

OpenAPI docs are generated at `GET /v3/api-docs` with Swagger UI at `/swagger-ui.html`.
Login is rate-limited: 5 failed attempts per username+IP within 5 minutes → `429`.

## Roadmap

- Stage 0 (done): skeleton + infra.
- Stage 1 (done): auth + full schema.
- Stage 2 (done): PDF parser (PDFBox, multi-page) + 3-step upload wizard + validation + dedup.
- Stage 3 (done): categorization — purpose/operation rules, flexible flat conditions (implicit AND), manual override.
- Stage 4 (done): dashboard + server-side analytics (`GET /api/analytics`) + category filter.
- Stage 5 (done): transfers (is_transfer + pairing heuristic) + gap detection.
- Stage 6 (done): frontend UX/design rework (sidebar app shell).
- Stage 7 (partly done): nginx reverse proxy (no TLS yet), OpenAPI/Swagger, login rate-limit — done;
  TLS, JSON logs and production hardening — pending (see checklist below).
- Stage 8 (done): bank-feed screenshot import (Tesseract OCR) — a fallback for banks that
  generate statements incorrectly; parses the transaction list screenshot (card number,
  amounts with `+`/`-`, merchant + category joined into «операция», «назначение платежа» left empty).
- Stage 9 (optional): notifications center, rule learning, mobile app.

## Production checklist (remaining Stage 7)

Deferred on purpose — add when going live:

1. **TLS on nginx.** Either a self-signed cert (browser warning, fine for a closed family VPS) or a real
   one (Let's Encrypt via `certbot`, needs a domain name + port 80 reachable). Then set `COOKIE_SECURE=true`
   and redirect HTTP→HTTPS in `frontend/nginx.conf`.
2. **JSON logs.** Switch the backend to `logback-spring.xml` with a `JsonEncoder` (structured
   `timestamp/level/logger/message`); keep console output for local dev.
3. **Production hardening.**
   - `restart: unless-stopped` on all services.
   - Stop publishing `postgres:5432` and `backend:8080` to the host — reach them only through nginx.
   - Run containers as non-root users; drop `BOOTSTRAP_*` after the first user is created.
4. **Backups.** A cron `pg_dump` of the `postgres` volume with rotation (e.g. keep 14 daily dumps).

## Notes

- Time is stored in UTC in the DB; the UI renders Europe/Moscow. Dates `DD.MM.YYYY`, money `1 234,56 ₽`.
- Secrets come from env / docker secrets only — never commit them.
