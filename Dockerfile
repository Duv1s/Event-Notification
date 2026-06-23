# syntax=docker/dockerfile:1
#
# Multi-stage build using the Gradle wrapper. Requires Java 26 base images (the project targets
# Java 26 + Spring Boot 4.1). If a Java 26 base image is not available, build the image without a
# Dockerfile instead:  ./gradlew bootBuildImage

# --- build stage ---
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew --no-daemon clean bootJar -x test

# --- runtime stage ---
FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]