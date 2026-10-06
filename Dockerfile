FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:25-jre

ARG GIT_COMMIT=unknown
ENV GIT_COMMIT=${GIT_COMMIT} \
    DB_PATH=/app/data/bg-offers

RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --home-dir /app app
WORKDIR /app
COPY --from=build /workspace/target/bg-offers-*.jar app.jar
RUN mkdir /app/data && chown -R app:app /app

USER app
EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
