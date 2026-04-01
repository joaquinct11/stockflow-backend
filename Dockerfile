# ========================================
# Multi-stage build: Maven + JRE 21
# ========================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy dependency manifests first for layer caching
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Create non-root user
RUN addgroup -S stockflow && adduser -S stockflow -G stockflow

COPY --from=build /app/target/*.jar app.jar

RUN chown stockflow:stockflow app.jar

USER stockflow

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
