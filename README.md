# Pulse Hive Service

Modular Spring Boot monolith for Portal26 Hive.

## Versions

| Component | Version |
|---|---|
| Java (JDK) | Eclipse Temurin **25** (LTS) |
| Spring Boot | **4.1.1** |
| Gradle (wrapper) | **9.1.0** |
| Packaging | jar · base package `com.portal26.hive` |

## Prerequisites

- **JDK 25** (Temurin 25 recommended). No system Gradle needed — `./gradlew` is bundled.
- Docker (for local Postgres + Redis), or running PostgreSQL and Redis instances.
- AWS credentials (e.g. `AWS_PROFILE`) that can call the existing Cognito User Pool.

## Build

```bash
./gradlew build
```

## Local Postgres + Redis (Docker Compose)

```bash
cp .env.example .env
# fill COGNITO_* and AWS_PROFILE in .env
docker compose up -d postgres redis
```

App on the host uses `DB_URL=jdbc:postgresql://localhost:5435/hive` and `REDIS_HOST=localhost` from `.env`.

## Run the API locally

```bash
cp .env.example .env
docker compose up -d postgres redis
./gradlew bootRun   # loads `.env` automatically
```

With `COGNITO_BOOTSTRAP_ADMIN=true`, startup uses the Cognito SDK (`AdminCreateUser` / `AdminSetUserPassword`) to ensure `admin@portal26.ai` exists in the **existing** user pool (no Terraform, no new pool).

## Staff login

- `POST /api/v1/auth/login` — body `{ "email", "password" }` → user JSON + `HIVE_SESSION` HttpOnly cookie
- `GET /api/v1/auth/me` — current user (requires cookie)
- `POST /api/v1/auth/logout` — clears Redis session + cookie

Seeded Hive staff: `admin@portal26.ai` / role `MSP_HIVE_ADMIN` (password only in Cognito, default bootstrap `Admin@123`).

### Token / session lifetimes

| Item | TTL |
|---|---|
| Cognito access / id token (configure on app client) | 15 minutes |
| Cognito refresh token (configure on app client) | 7 days |
| Redis session + cookie | 7 days (`HIVE_SESSION_TTL`) |

Refresh tokens are stored **only in Redis** (server-side). The browser cookie holds the opaque session id only.

## Run API + Postgres + Redis in Docker

```bash
docker compose --profile full up --build
```

## Configuration

Single `application.yml` with `${...}` placeholders.
Values come from environment variables (local `.env`, EKS env, or AWS Secrets Manager).
Secrets must not be committed.

## Actuator

Health check: `GET /actuator/health`. Other endpoints are controlled by `ACTUATOR_ENDPOINTS`.
