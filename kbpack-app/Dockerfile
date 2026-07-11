FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21.0.5_11-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S -g 10001 kbpack \
    && adduser -S -D -H -u 10001 -G kbpack kbpack

WORKDIR /app

COPY --from=build --chown=kbpack:kbpack /workspace/target/*.jar /app/app.jar

RUN mkdir -p /app/logs /tmp/kbpack \
    && chown -R kbpack:kbpack /app /tmp/kbpack

USER kbpack

EXPOSE 18080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:18080/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
