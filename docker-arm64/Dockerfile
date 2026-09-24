# Multi-stage build. The fat jar is built NATIVELY by the CI runner (`./gradlew bootJar`, no
# QEMU) and copied into docker-arm64/ before `docker build` runs (see the workflows in
# .github/workflows/) — this Dockerfile only assembles the arm64 runtime image on top of it.
#
# Build context is docker-arm64/ (the workflows `cd` into this directory before building) —
# the COPY paths below are relative to this directory, not the repo root.

# --- builder: package the prebuilt jar + optional Datadog agent (native, no Gradle) ---
FROM eclipse-temurin:25-alpine AS builder
WORKDIR /workspace

# Fat jar built by the CI runner and copied into docker-arm64/ before `docker build`.
# TODO: match whatever filename your CI staging step actually produces (see the "Stage jar
# for docker build" step in .github/workflows/ci.yml and deploy-*.yml).
ARG JAR_FILE=hive-service.jar
COPY ${JAR_FILE} /workspace/app.jar

# Optional: fetch the Datadog agent at build time (only when enabled). Delete this block
# entirely if you don't want Datadog tracing wired in.
ARG DD_AGENT_ENABLED=false
ARG DD_AGENT_VERSION=1.42.0
RUN if [ "$DD_AGENT_ENABLED" = "true" ]; then \
      wget -O /workspace/dd-java-agent.jar \
        "https://dtdg.co/latest-java-tracer" ; \
    else \
      : > /workspace/dd-java-agent.jar ; \
    fi

# --- runtime ---
# TODO: if your app targets a Java version other than 25, change this tag to match — it must
# agree with the JDK version used to build the jar in CI.
FROM --platform=arm64 gcr.io/distroless/java25-debian13 AS runtime

# Non-root user, uid/gid 1000 — matches the platform's securityContext (runAsUser/runAsGroup
# 1000 in deployment/base/deployment.yml). Do not change one without the other.
COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group

# NOTE: APP_HOME/LOG_HOME use "granger" for historical reasons tied to Portal26's internal
# tooling naming — it has no meaning specific to your service. Safe to keep as-is, or rename
# to anything you like as long as every path below stays consistent with it.
ENV APP_HOME=/usr/local/granger \
    LOG_HOME=/var/log/granger
WORKDIR ${APP_HOME}

COPY --from=builder /workspace/app.jar ${APP_HOME}/hive-service.jar
COPY --from=builder /workspace/dd-java-agent.jar ${APP_HOME}/dd-java-agent.jar
# You must supply these two files — see the samples included alongside this Dockerfile
# (application.properties, logback.xml) and the README "Logging & health config" section.
COPY application.properties ${APP_HOME}/application.properties
COPY logback.xml ${APP_HOME}/logback.xml

USER 1000:1000
EXPOSE 8080

ENV JDK_JAVA_OPTIONS="-DLOG_HOME=${LOG_HOME} -Dlogging.config=file://${APP_HOME}/logback.xml" \
    JAVA_TOOL_OPTIONS="-Xms512m -Xmx512m"

# Distroless java entrypoint is `java`; pass the jar + external config. Use
# --spring.config.additional-location (not --spring.config.location) so this external file
# ADDS to your packaged application.yml/profile YAMLs instead of replacing them outright.
CMD [ "/usr/local/granger/hive-service.jar", \
      "--spring.config.additional-location=file:///usr/local/granger/application.properties" ]
