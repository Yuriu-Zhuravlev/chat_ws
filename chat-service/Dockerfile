# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
# Build context is the repository root. Only the parent pom and this one module are
# copied: the parent is resolved through <relativePath>, and building with -f instead
# of the reactor keeps sibling modules out of this image's cache key, so a change in
# chat does not rebuild notification.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY .mvn ./.mvn
COPY mvnw ./mvnw
COPY chat-service ./chat-service

# Git on Windows does not preserve the executable bit.
RUN chmod +x ./mvnw

# Cache mount: the local repository is downloaded once and shared by every service image.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -f chat-service/pom.xml -DskipTests package

# ---- runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# curl is here only so the healthcheck below has something to run with.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 app

USER app
WORKDIR /app
COPY --from=build --chown=app:app /build/chat-service/target/*.jar app.jar

EXPOSE 8082

# start-period covers JVM warm-up and Flyway; without it Compose would mark the
# container unhealthy before it has had a chance to finish starting.
HEALTHCHECK --interval=10s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
