# ─────────────────────────────────────────────
# Backend Dockerfile
# ─────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

RUN mkdir -p /app/uploads/media

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]