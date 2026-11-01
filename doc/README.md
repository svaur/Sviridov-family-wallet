# Sviridov family wallet — documentation index

Self-hosted personal/family finance tracker for 1–3 users. It ingests bank
statements (PDF) and bank-feed screenshots, normalizes/categorizes the
transactions, and shows a dashboard. Everything runs on one VPS via
`docker compose`.

> Start here if you are an AI agent or a developer who has no prior context.

## Read in this order

| Doc | What it answers |
|---|---|
| [architecture.md](architecture.md) | What the system is made of, how a request flows, how it is deployed |
| [data-model.md](data-model.md) | Database tables, columns, invariants, Flyway migrations V1–V11 |
| [domain-logic.md](domain-logic.md) | The non-obvious business rules: import pipeline, dedup/fingerprint, categorization, excluded category, gap detection, screenshot OCR |
| [backend.md](backend.md) | Java package layout, every service/controller and its responsibility |
| [frontend.md](frontend.md) | React pages, components, routing, design system, shared helpers |
| [api.md](api.md) | REST endpoint reference |
| [development.md](development.md) | Build/run/test, toolchain quirks, environment variables |
| [testing.md](testing.md) | Unit vs integration tests, how to run them, what they cover |

## 30-second overview

- **Stack**: Java 21 + Spring Boot 3.3.5 (web/security/data-jpa/Flyway) +
  PostgreSQL 16 + Apache PDFBox + Tesseract OCR; React 18 + TypeScript + Vite +
  Recharts; nginx serving the SPA and proxying `/api`.
- **Auth**: cookie session (HttpOnly, SameSite=Strict, Secure in prod), BCrypt,
  manual users (no self-registration).
- **Data isolation**: every row is scoped by `user_id`; services always receive
  the authenticated user's id and verify ownership.
- **Money model**: `transactions` is the single source of truth; `is_transfer`,
  `is_excluded` and the system "исключено" category keep excluded rows out of
  analytics; `category_manual` protects hand-assigned categories from rule
  re-application.
- **Import pipeline**: parse file → validate → categorize → preview on the
  frontend → confirm → persist (dedup by `fingerprint`). The backend keeps **no
  draft state** — the wizard state lives in the browser.
- **PDF is never stored**: only statement metadata is persisted.
- **Rules**: one flat JSON condition set per rule (`operation_contains`,
  `operation_regex`, `purpose_contains`, `purpose_regex`, `min`, `max`,
  `day_from`, `day_to`), implicit AND, first match by priority wins.

## Repository layout

```
backend/            Spring Boot service
  src/main/java/ru/sviridov/wallet/
    domain/         JPA entities + repositories
    parser/         StatementParser implementations (PDFBox, Tesseract) + DTOs
    service/        business logic
    web/            REST controllers, DTOs, error handling
    security/       Spring Security config, UserDetails
    config/         bootstrap (admin user creation)
  src/main/resources/db/migration/   Flyway V1–V11
frontend/           React + Vite SPA (+ nginx.conf, Dockerfile)
docker-compose.yml  postgres + backend + frontend(nginx)
doc/                this documentation
AGENTS.md           conventions for AI agents working on this repo
```
