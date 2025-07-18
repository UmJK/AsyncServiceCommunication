# Use a stable and cross-platform compatible base image
FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

# Set working directory
WORKDIR /app

# Expose application port
EXPOSE 8080
