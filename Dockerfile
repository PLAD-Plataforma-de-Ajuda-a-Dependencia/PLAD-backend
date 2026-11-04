# syntax=docker/dockerfile:1

# ---- build: Maven + JDK 25 ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# dependencies in their own layer, so a source change doesn't download everything again
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# tests need Docker (Testcontainers), so they run in CI, not here
COPY src ./src
RUN mvn -B -q -DskipTests package \
    && mv target/plad-backend-*.jar target/app.jar

# ---- runtime: JRE 25, non-root ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /build/target/app.jar app.jar
USER app

# prod needs DB_URL, DB_USER, DB_PASSWORD and JWT_SECRET (no defaults): see docs/deploy.md
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# sh + exec: $JAVA_OPTS is expanded and the JVM still becomes PID 1 and receives SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
