# ==============================================================================
# FlowDesk Backend - Multi-Stage Containerization
# Build stage: Maven 3.9 + Eclipse Temurin JDK 21 (Alpine)
# Runtime stage: Eclipse Temurin JRE 21 (Alpine)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build executable Spring Boot application archive
# ------------------------------------------------------------------------------
ARG BUILDPLATFORM
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Cache dependency layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source tree and package application
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal, secure runtime container
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Create non-root system group and user
RUN addgroup -g 10001 -S flowdesk && \
    adduser -u 10001 -S flowdesk -G flowdesk

WORKDIR /app

# Prepare storage directory for ticket attachments and set permissions
RUN mkdir -p /app/data/attachments && \
    chown -R flowdesk:flowdesk /app

# Copy executable jar from builder stage
COPY --from=builder --chown=flowdesk:flowdesk /workspace/target/backend-*.jar /app/app.jar

USER flowdesk

EXPOSE 8080

# Health check using FlowDesk's public /api/health endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/health || exit 1

# Launch Spring Boot with container memory optimization and secure entropy source
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "/app/app.jar"]
