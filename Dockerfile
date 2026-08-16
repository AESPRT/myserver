# ==========================================
# Build stage
# ==========================================
FROM gradle:8.10-jdk21 AS builder

WORKDIR /app

# Copy Gradle configuration first
# This allows Docker to cache dependency downloads.
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

RUN gradle dependencies --no-daemon

# Copy application source
COPY src ./src

# Build Spring Boot application
RUN gradle bootJar --no-daemon


# ==========================================
# Runtime stage
# ==========================================
FROM eclipse-temurin:21-jre

# Install curl for Docker health checks
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]