# Sviridov Family Wallet

Self-hosted personal/family finance tracker built on PDF bank statements.

**Stack:** Java 21 · Spring Boot 3 (web, data-jpa, validation, security, actuator) · Flyway · PostgreSQL ·
Apache PDFBox · React + TypeScript + Vite · React Router · Recharts.

## Repository layout

```
backend/            Spring Boot 3 single-module app (Maven)
frontend/           React + TypeScript + Vite SPA
docker-compose.yml  postgres + backend
.env.example        env template (copy to .env)
```

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker + Docker Compose
- Node.js 20+ and pnpm (for the frontend)

## Run (development)

1. `cp .env.example .env` and set real passwords (Postgres + bootstrap user).
2. `docker compose up --build` — starts PostgreSQL and the backend on `:8080`.
   - Health: `GET http://localhost:8080/actuator/health`
   - On first start, Flyway creates the full schema (V1–V8).
   - If `BOOTSTRAP_USERNAME` / `BOOTSTRAP_PASSWORD` are set, the first user is created automatically.
3. Frontend (separate terminal):
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
- Endpoints: `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`.

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

Statements (3-step PDF upload wizard, ≤10 MB, PDF not stored):
- `POST /api/statements/preview` — multipart `file`; parse + validate + categorise → draft preview
- `POST /api/statements/preview/{draftId}/recategorize` — re-apply rules to the draft
- `DELETE /api/statements/preview/{draftId}/transactions/{rowNumber}` — remove a row from the draft
- `POST /api/statements/preview/{draftId}/transactions/{rowNumber}/category` — manual category in the draft
- `POST /api/statements/preview/{draftId}/confirm` — import (dedup, save statement + transactions)
- `GET /api/statements` — upload history (all cards)
- `GET /api/statement-gaps` — detected gaps between statements

Transactions:
- `GET /api/transactions` — list my transactions
- `POST /api/transactions` — manual entry (`source` = card|cash|other)
- `DELETE /api/transactions/{id}` — soft-delete
- `POST /api/transactions/{id}/category` — assign category manually
- `POST /api/transactions/{id}/transfer` — mark as transfer (excluded from stats)

Analytics:
- `GET /api/analytics?from=&to=&exclude=1,2,3` — KPI + monthly + per-category (excludes transfers & excluded)

## Roadmap

- Stage 0 (done): skeleton + infra.
- Stage 1 (done): auth + full schema.
- Stage 2 (done): PDF parser (PDFBox, multi-page) + 3-step upload wizard + validation + dedup.
- Stage 3 (done): categorization — purpose/operation rules, flexible flat conditions (implicit AND), manual override.
- Stage 4 (done): dashboard + server-side analytics (`GET /api/analytics`) + category filter.
- Stage 5 (done): transfers (is_transfer + pairing heuristic) + gap detection.
- Stage 6 (done): frontend UX/design rework (sidebar app shell).
- Stage 7: production readiness (nginx/TLS, JSON logs, OpenAPI, rate-limit).
- Stage 8 (optional): notifications center, rule learning, mobile app.

## Notes

- Time is stored in UTC in the DB; the UI renders Europe/Moscow. Dates `DD.MM.YYYY`, money `1 234,56 ₽`.
- Secrets come from env / docker secrets only — never commit them.
