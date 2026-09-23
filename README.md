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
- AWS credentials (e.g. `AWS_PROFILE`) that can call the existing Cognito User Pool.

## Build

```bash
./gradlew build
```

## Local Postgres (Docker Compose)

```bash
cp .env.example .env
# fill COGNITO_* and AWS_PROFILE in .env
docker compose up -d postgres
```

App on the host uses `DB_URL=jdbc:postgresql://localhost:5435/hive` from `.env`. Login sessions and OAuth PKCE state are stored in Postgres (`hive_session`, `oauth_state`).

## Run the API locally

```bash
cp .env.example .env
docker compose up -d postgres
./gradlew bootRun   # loads `.env` automatically
```

With `COGNITO_BOOTSTRAP_ADMIN=true`, startup uses the Cognito SDK (`AdminCreateUser` / `AdminSetUserPassword`) to ensure a local Cognito user exists in the **existing** user pool (dev only). Access control for who can sign in is owned by **Cognito**; Hive does not maintain a staff allowlist or role checks.

## Staff login (Cognito Hosted UI)

Interactive login uses **AWS Cognito Hosted UI** (authorization code + PKCE). Hive does **not** render an email/password form.

| Step | Endpoint / action |
|---|---|
| Start login | `GET /api/v1/auth/login` → **302** to Cognito Hosted UI |
| Cognito callback | `GET /api/v1/auth/callback?code=&state=` → exchange code, **find-or-create** `staff` by email, set `HIVE_SESSION`, **302** to frontend |
| Current user | `GET /api/v1/auth/me` (requires cookie) |
| Logout | `POST /api/v1/auth/logout` → clear DB session + cookie, **302** to Cognito `/logout` |

Frontend “Sign in” should **navigate** (full page) to `{API}/api/v1/auth/login`, not POST credentials.

On first successful Cognito login, Hive inserts a `staff` row (email only; roles are not validated in Hive). Later logins reuse that row.
### Cognito app client (AWS / Terraform)

Managed by Hive-Poc Terraform: `portal26-hive-auth-poc/infra/cognito`.

```bash
cd ~/Hive-Poc/portal26-hive-auth-poc/infra/cognito
AWS_PROFILE=aws-kunal-rathod terraform apply
```

Key outputs / `.env` mapping:

| Env var | Source |
|---|---|
| `COGNITO_DOMAIN` | `cognito_domain` output |
| `COGNITO_REDIRECT_URI` | `hive_api_callback_url` (`http://localhost:8080/api/v1/auth/callback`) |
| `COGNITO_LOGOUT_URI` | `hive_api_logout_url` |
| `COGNITO_CLIENT_ID` / `SECRET` | backend app client (`portal26-hive-backend`) |

Backend app client: authorization code + PKCE, IdP `COGNITO` (native email/password Hosted UI), callback = Hive API.

### Hosted UI branding (AWS Console — not Hive code)

Keep the existing Portal26 Cognito card (dark header, email/password, Forgot password, Sign in). Customize header in Cognito branding:

- Small centered **portal26** logo
- Large centered title **Hive** underneath

Apply via Cognito → User pool → App integration → Domain / Managed Login branding (logo upload and/or custom CSS). Preview in AWS before wiring production redirect URLs.

### Token / session lifetimes

| Item | TTL |
|---|---|
| Cognito access / id token (configure on app client) | 15 minutes |
| Cognito refresh token (configure on app client) | 7 days |
| Postgres `hive_session` + cookie | 7 days (`HIVE_SESSION_TTL`) |
| OAuth `state` + PKCE verifier | 10 minutes (`oauth_state` table) |

Refresh tokens are stored **only in Postgres** (`hive_session`, server-side). The browser cookie holds the opaque session id only.

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
