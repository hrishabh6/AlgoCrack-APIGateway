# Stage 1: Extract
FROM eclipse-temurin:21-jre AS builder
WORKDIR /app
# Expects the CI/Host to have built the artifact already
COPY build/libs/*.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Create a non-root user
RUN addgroup --system spring && adduser --system --group spring
USER spring:spring

COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Using the JarLauncher for Spring Boot >= 3.2.0
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
