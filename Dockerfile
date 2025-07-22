# Multi-stage build for optimal image size
FROM gradle:8.5-jdk17 AS build

# Set working directory
WORKDIR /app

# Copy gradle configuration files first (for better caching)
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon

# Copy source code
COPY src src

# Build application
RUN gradle clean shadowJar --no-daemon

# Production stage
FROM amazoncorretto:17-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 app && \
    adduser -D -s /bin/sh -u 1001 -G app app

# Set working directory
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Change ownership
RUN chown -R app:app /app

# Switch to non-root user
USER app

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]
