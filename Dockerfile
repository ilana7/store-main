# syntax=docker/dockerfile:1

# ---- build ------------------------------------------------------------------
# Self-contained: the image can be built from a clean checkout with no local
# toolchain. The Gradle cache is mounted so repeat builds do not re-download.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Copy the build definition first so dependency resolution is cached independently
# of source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar

# Spring Boot layered jars: dependencies change far less often than application
# code, so extracting them into separate layers keeps image pushes small.
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination layers

# ---- runtime ----------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# Run as an unprivileged user rather than root.
RUN useradd --system --create-home --uid 10001 --shell /usr/sbin/nologin spring
WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/layers/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/layers/application/ ./

USER spring
EXPOSE 8080

# Honour container memory limits rather than assuming the host's.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
