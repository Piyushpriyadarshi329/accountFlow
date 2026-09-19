# syntax=docker/dockerfile:1

# =============================================================================
# AccountFlow API - container image for Render (or any Docker host).
#
# Build:  docker build -t accountflow-api .
# Run:    docker run -p 8082:8082 \
#           -e MONGODB_URI="mongodb+srv://..." \
#           -e JWT_SECRET="$(openssl rand -base64 48)" \
#           accountflow-api
#
# No secret is baked into the image. MONGODB_URI and JWT_SECRET are read from
# the environment at start-up, and .dockerignore keeps the local .env out of
# the build context entirely.
# =============================================================================

# --- build -------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies resolve from the POM alone, so this layer is reused on every
# build where only source changed - which is almost all of them.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped here deliberately: a deploy should ship an artefact CI has
# already tested, and the integration suite needs a Docker daemon this build
# does not have. Run `./mvnw test` in CI, not during the image build.
RUN mvn -B -q clean package -DskipTests

# --- split the jar into layers -----------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS layers
WORKDIR /layers
COPY --from=build /build/target/*.jar app.jar
# The destination has to be an empty directory - extracting into "." alongside
# app.jar fails with "already exists and is not empty".
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# --- runtime -----------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# A compromised process should not be root inside the container.
RUN groupadd --system spring && useradd --system --gid spring --home /app spring

# Each layer is copied separately so Docker can cache them independently: the
# 38 MB dependency layer only changes when the POM does, while the application
# layer is ~200 KB and changes every build.
#
# They must all land in the SAME directory. The extracted app.jar declares
# "Class-Path: lib/...", so the dependency layer has to sit beside it, not in a
# sibling folder.
COPY --from=layers --chown=spring:spring /layers/extracted/dependencies/ ./
COPY --from=layers --chown=spring:spring /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=spring:spring /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=spring:spring /layers/extracted/application/ ./

USER spring

# Documentation only. The app binds whatever PORT the platform injects; Render
# sets it, and application.properties falls back to 8082 when it is absent.
EXPOSE 8082

# MaxRAMPercentage rather than a fixed -Xmx, so the heap tracks the container
# limit instead of the host's memory. SerialGC suits a small single-CPU
# instance, where G1's background threads cost more than they return.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
