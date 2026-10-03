# ---- Stage 1: build the jar with the full JDK ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Copy only the build files first so the dependency layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests already run in the Jenkins "Build & Test" stage
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: small runtime image, JRE only, no build tools ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Numeric non-root user so Kubernetes can enforce runAsNonRoot
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=build /src/target/app.jar app.jar
USER 10001

EXPOSE 8080
# Docker marks the container unhealthy if the app stops answering
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Size the heap from the container memory limit instead of the host's RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
