# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:26-jdk-alpine AS builder
WORKDIR /app

# Copy Gradle wrapper and build scripts first (better layer caching)
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# Pre-fetch dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon --quiet || true

# Copy sources and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:26-jre-alpine AS runtime
WORKDIR /app

# Non-root user for security
RUN addgroup -S andromeda && adduser -S andromeda -G andromeda
USER andromeda

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
