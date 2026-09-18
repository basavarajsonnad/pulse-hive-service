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
- Docker (for local Postgres), or a running PostgreSQL instance.

## Build

```bash
./gradlew build
```

## Local Postgres (Docker Compose)

```bash
cp .env.example .env
docker compose up -d postgres
```

App on the host uses `DB_URL=jdbc:postgresql://localhost:5435/hive` from `.env`.

## Run the API locally

```bash
cp .env.example .env
docker compose up -d postgres
./gradlew bootRun   # loads `.env` automatically
```

## Run API + Postgres in Docker

```bash
docker compose --profile full up --build
```

## Configuration

Single `application.yml` with `${...}` placeholders.
Values come from environment variables (local `.env`, EKS env, or AWS Secrets Manager).
Secrets must not be committed.

## Actuator

Health check: `GET /actuator/health`. Other endpoints are controlled by `ACTUATOR_ENDPOINTS`.
