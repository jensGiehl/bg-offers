FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:25-jre

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /workspace/target/bg-offers-*.jar app.jar
RUN mkdir /app/data && chown -R app:app /app

USER app
EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
