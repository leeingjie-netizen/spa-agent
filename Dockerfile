FROM maven:3.9.4-eclipse-temurin-17@sha256:e3f36fd6d0949541de3b6a32bb3de30331b2bdb6bac8ba365c4f8a5f0fcd3e59 AS build
WORKDIR /build
COPY pom.xml .
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy@sha256:c6a5dd70fb24f4e07ef88cbda8efb613a5a649a1af071a41de8eaabd1ee6b5da
RUN useradd --uid 10001 --create-home spa
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/spa-agent-0.3.0-rc.1.jar /app/app.jar
COPY --from=build --chown=10001:10001 /build/target/classes/com/lee/spa/ContainerHealthProbe.class /app/health/com/lee/spa/ContainerHealthProbe.class
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD ["java","-cp","/app/health","com.lee.spa.ContainerHealthProbe"]
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/app.jar"]
