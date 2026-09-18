# Pulse Hive Service

Modular Spring Boot monolith for Portal26 Hive.

## Build

```bash
./gradlew build
```

## Local Postgres (Docker Compose)

```bash
cp .env.example .env
docker compose up -d postgres
```

App on the host uses `DB_URL=jdbc:postgresql://localhost:5432/hive` from `.env`.

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
