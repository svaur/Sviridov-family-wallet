# Sviridov Family Wallet

Self-hosted personal/family finance tracker built on PDF bank statements.

**Stack:** Java 21 · Spring Boot 3 (web, data-jpa, validation, security, actuator) · Flyway · PostgreSQL ·
Apache PDFBox · React + TypeScript + Vite · React Router.

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
   - On first start, Flyway creates the full schema (V1, V2).
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

Cards:
- `GET /api/cards` — list my cards
- `POST /api/cards` — create (`{ displayName, bank?, last4?, currency? }`)
- `PUT /api/cards/{id}` — update
- `DELETE /api/cards/{id}` — archive (soft)

Statements (PDF upload, ≤10 MB):
- `POST /api/cards/{cardId}/statements` — multipart `file`; parses, validates, dedups; returns
  `{ statementId, status, rowCountRaw, importedCount, skippedDuplicates, warnings[], unrecognizedRows[] }`.
  The PDF is **not** stored — only import metadata goes to the `statements` table.
- `GET /api/cards/{cardId}/statements` — upload history for a card.

All errors share one shape:
`{ timestamp, status, error, message, path, fieldErrors[] }` (400 validation, 401, 403, 404, 500).

## Roadmap

- Stage 0 (done): skeleton + infra.
- Stage 1 (done): auth + full schema + cards CRUD.
- Stage 2 (done): PDF parser (PDFBox) + statement upload + validation + dedup.
- Stage 3 (done): merchant normalization + categorization (default + user rules, priority; flexible flat-condition rules with implicit AND).
- Stage 4: dashboard + analytics (`GET /api/analytics`).
- Stage 5: transaction editing + learning.
- Stage 6: gaps + transfers + notifications.
- Stage 7: production readiness (nginx/TLS, JSON logs, OpenAPI, rate-limit).

## Notes

- Time is stored in UTC in the DB; the UI renders Europe/Moscow. Dates `DD.MM.YYYY`, money `1 234,56 ₽`.
- Secrets come from env / docker secrets only — never commit them.
