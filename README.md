# Sviridov Family Wallet

Self-hosted personal/family finance tracker built on PDF bank statements.

**Stack:** Java 21 · Spring Boot 3 (web, data-jpa, validation, actuator) · Flyway · PostgreSQL ·
React + TypeScript + Vite · React Router. PDFBox arrives in Stage 2; security in Stage 1.

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

1. `cp .env.example .env` and set a real password.
2. `docker compose up --build` — starts PostgreSQL and the backend on `:8080`.
   - Health: `GET http://localhost:8080/actuator/health`
   - Smoke:  `GET http://localhost:8080/api`
3. Frontend (separate terminal):
   ```bash
   cd frontend
   pnpm install
   pnpm dev          # http://localhost:5173, proxies /api and /actuator to :8080
   ```

## Roadmap

Stage 0 (current): skeleton + infra. Stage 1: auth + full schema + cards CRUD.
See the project plan for the remaining stages (PDF parser, categorization, analytics, …).

## Notes

- Time is stored in UTC in the DB; the UI renders Europe/Moscow.
- Secrets come from env / docker secrets only — never commit them.
