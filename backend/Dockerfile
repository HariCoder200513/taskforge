# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the jar ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first (changes to pom invalidate this layer, not src edits).
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# Tests run in the CI pipeline (they need Docker/Testcontainers), not the image build.
RUN mvn -B -ntp -DskipTests clean package

# ---- Runtime stage: slim JRE, non-root ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
