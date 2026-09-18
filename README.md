# Pulse Hive Service

Spring Boot service scaffold generated via [Spring Initializr](https://start.spring.io/).

## Versions

| Component | Version |
|---|---|
| Java (JDK) | Eclipse Temurin **25.0.4.1** (LTS) |
| Spring Boot | **4.1.1** |
| Maven (wrapper) | **3.9.16** |
| Lombok | **1.18.46** |
| Packaging | jar · base package `com.portal26.hive` |

**Dependencies:** Spring Web (webmvc), Spring Data JPA, PostgreSQL Driver, Lombok, Actuator.

## Prerequisites

- **JDK 25** (Temurin 25 recommended). No system Maven needed — `./mvnw` is bundled.
- A running **PostgreSQL** instance (see [Configuration](#configuration)).

> This project targets Java 25 (committed in `.java-version`). Point `JAVA_HOME` at it
> without changing your machine default:
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS
> ```

## Build & run

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw clean package          # build
./mvnw spring-boot:run        # run  → http://localhost:8080
./mvnw test                   # test
```

> Spring Data JPA + PostgreSQL are on the classpath, so the app needs a datasource at
> startup or it fails with *"Failed to configure a DataSource."* Configure one first.

## Configuration

`src/main/resources/application.properties`:

```properties
spring.application.name=pulse-hive-service

spring.datasource.url=jdbc:postgresql://localhost:5432/pulse_hive
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.jpa.hibernate.ddl-auto=update
```

Keep credentials in env vars or a `application-local.properties` profile — don't commit secrets.

## Actuator

Health check: `GET /actuator/health`. Expose more via
`management.endpoints.web.exposure.include`.
