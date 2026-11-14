FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/ ./
RUN mvn -B test package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --uid 10001 --create-home app && mkdir /data && chown app:app /data
WORKDIR /app
ARG SERVICE
COPY --from=build /build/${SERVICE}/target/${SERVICE}-0.1.0.jar /app/service.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
