# Development

## Prerequisites

- Java 21 (LTS; **not** 22+ for the local Maven test run — see quirks)
- Maven 3.9+
- Docker + Docker Compose
- Node.js 22.5+ (pnpm 11 uses the built-in `node:sqlite`) and pnpm
- (optional, local only) `tesseract-ocr` + Russian language data — the Docker
  image already contains it

## Build & run (Docker — the normal way)

```bash
cp .env.example .env          # fill POSTGRES_* and BOOTSTRAP_*
docker compose up --build
```

- app: <http://localhost>
- backend: <http://localhost:8080>, Swagger: <http://localhost:8080/swagger-ui.html>
- postgres: `localhost:5432`
- The bootstrap user is created on first start from `BOOTSTRAP_USERNAME` /
  `BOOTSTRAP_PASSWORD` (password ≥ 8 chars).
- Rebuild after code changes: `docker compose up -d --build backend` (or
  `--build frontend`). `docker compose down -v` wipes the DB volume.

Files: `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`,
`frontend/nginx.conf`.

## Local dev (without Docker)

```bash
# backend
cd backend
mvn spring-boot:run          # needs a reachable Postgres via DB_URL/DB_USERNAME/DB_PASSWORD

# frontend
cd frontend
pnpm install
pnpm dev                     # Vite on :5173, proxies /api and /actuator to :8080
```

## Tests

```bash
cd backend && mvn test        # 82 tests, 1 skipped (end-to-end OCR needs local tesseract)
cd frontend && pnpm build     # tsc -b + vite build
```

Unit tests do not start a Spring context; they construct services manually with
Mockito mocks. Parser tests use sanitized fixtures in
`backend/src/test/resources/` (`ТЕСТОВАЯ_выписка_отп.pdf`, `screenshot_test.png`).

## Environment variables

| Variable | Default | Notes |
|---|---|---|
| `POSTGRES_DB` | `sviridov_wallet` | compose only |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `sviridov` | **applied only on first init** of an empty volume |
| `DB_URL` | `jdbc:postgresql://localhost:5432/sviridov_wallet` | backend |
| `DB_USERNAME` / `DB_PASSWORD` | `sviridov` | backend |
| `COOKIE_SECURE` | `false` | set `true` behind TLS |
| `BOOTSTRAP_USERNAME` / `BOOTSTRAP_PASSWORD` | empty | admin user bootstrap |
| `app.gap.days` | `7` | statement-gap threshold |

Never commit `.env`. `.env.example` contains placeholders only.

## Quirks / gotchas

- **JDK version for `mvn test`**: with JDK 22+ the Mockito inline mock maker
  fails ("Could not modify all classes"). Use JDK 21 for tests
  (`JAVA_HOME=<jdk21>`). The Docker build uses `eclipse-temurin:21`.
- **pnpm / `node:sqlite`**: pnpm 11 requires Node ≥ 22.5; the frontend
  Dockerfile therefore uses `node:24-alpine`. Locally use Node 22+.
- **pnpm build settings**: `frontend/pnpm-workspace.yaml` holds
  `allowBuilds: { esbuild: true }` (pnpm 11 moved this out of `package.json`).
- **Recharts chunk warning**: `chunk > 500 kB` is cosmetic; the bundle is a
  single page app with Recharts.
- **Screenshot OCR is slow**: each operation is a separate Tesseract process
  (~1–2 min per ~90-operation screenshot). nginx has `proxy_read_timeout 300s`;
  do not lower it.
- **Uploads**: max 10 MB (`spring.servlet.multipart`), nginx
  `client_max_body_size 12m`.
- **Docker buildx in sandboxes**: if `docker compose build` fails with
  "failed to update builder last activity time", use
  `DOCKER_BUILDKIT=0 docker compose up -d --build backend`.

## Definition of done for a change

1. `mvn test` green (JDK 21) and `pnpm build` green.
2. Update the relevant file(s) in `doc/` (this folder) if behaviour, schema,
   endpoints or architecture changed.
3. Do **not** commit: leave the working tree clean-ish and report the diff; the
   user commits and pushes.
