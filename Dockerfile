FROM maven:3.8.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build application
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Expose port
EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
