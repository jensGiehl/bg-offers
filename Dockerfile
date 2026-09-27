FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:25-jre-noble AS java-runtime

FROM mcr.microsoft.com/playwright/java:v1.63.0-noble

ARG GIT_COMMIT=unknown
ENV GIT_COMMIT=${GIT_COMMIT} \
    JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:${PATH}"

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
COPY --from=build /workspace/target/bg-offers-*.jar app.jar
RUN mkdir /app/data && chown -R app:app /app

USER app
EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["xvfb-run", "--auto-servernum", "java", "-jar", "/app/app.jar"]
