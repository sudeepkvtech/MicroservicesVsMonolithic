# Multi-stage Docker build for Spring Boot application
# Design Pattern: Multi-stage Build Pattern
# - Reduces final image size by separating build and runtime stages
# - Build artifacts in one stage, copy only necessary files to runtime stage

# Stage 1: Build stage
# Uses Maven image to compile and package the application
FROM maven:3.9-eclipse-temurin-17 AS build

# Set working directory inside container
WORKDIR /app

# Copy pom.xml first (layer caching optimization)
# Design Pattern: Layer Caching
# - Docker caches layers that don't change
# - pom.xml changes less frequently than source code
# - If pom.xml unchanged, Maven doesn't re-download dependencies
COPY pom.xml .

# Download dependencies (this layer is cached if pom.xml unchanged)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
# -DskipTests: Skip tests during Docker build (run tests in CI/CD instead)
# package: Creates JAR file
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage
# Uses smaller JRE image (no Maven, no build tools)
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy JAR from build stage
# Only the compiled JAR is copied, not source code or Maven dependencies
# This keeps the runtime image small
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080 (default Spring Boot port)
EXPOSE 8080

# Run the application
# -Djava.security.egd: Improves startup time
# -Dspring.profiles.active: Can be overridden by environment variable
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]

# Health check (Docker can restart container if unhealthy)
# Checks if application is responding on health endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Image size optimization:
# - Multi-stage build: ~150MB instead of ~600MB
# - Alpine Linux: Minimal base image
# - JRE instead of JDK: Smaller runtime
