FROM openjdk:17-jdk-slim
WORKDIR /app
COPY ./build/libs/ev-charging-ktor-0.0.1-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
