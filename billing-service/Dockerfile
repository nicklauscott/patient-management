# ---------- Stage 1: Build ----------
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first (to leverage Docker cache)
COPY gradle ./gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Download dependencies to cache them
RUN ./gradlew dependencies --no-daemon

# Now copy the full source
COPY src ./src

# Build the application JAR
RUN ./gradlew clean bootJar --no-daemon

# ---------- Stage 2: Run ----------
FROM openjdk:21-jdk-slim AS runner

WORKDIR /app

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 4001
EXPOSE 9001

ENTRYPOINT ["java", "-jar", "app.jar"]