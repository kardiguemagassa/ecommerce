# syntax=docker/dockerfile:1
#   docker build -t ecommerce-api .
# Tests are not run here: they need Docker themselves (Testcontainers) and run in CI before any image is built.

ARG JAVA_VERSION=21

# Java bytecode is the same on every platform: compile once, natively, then assemble a runtime per platform.
FROM --platform=$BUILDPLATFORM eclipse-temurin:${JAVA_VERSION}-jdk AS build
# The Maven wrapper (only-script) downloads and unpacks Maven itself.
RUN apt-get update && apt-get install --yes --no-install-recommends curl unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace

# Maven in its own image layer, outside the cache mount used below.
ENV MAVEN_USER_HOME=/opt/maven-wrapper
COPY .mvn/ .mvn/
COPY mvnw ./
RUN ./mvnw --batch-mode --version > /dev/null

# POM first: the dependency download stays cached until pom.xml changes.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --quiet dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --quiet package -DskipTests \
    && java -Djarmode=tools -jar target/ecommerce-*.jar \
        extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:${JAVA_VERSION}-jre AS runtime
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app

# Least to most frequently changed: a code change only rebuilds and ships the last layer.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080 8081
# - G1, chosen explicitly: below 2 CPUs or 1792 MB the JVM would pick the single-threaded Serial collector.
# - Heap sized from the container memory limit: 60 %, the rest for metaspace, code cache and threads.
# - Restart cleanly rather than limp on after an OutOfMemoryError.
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
