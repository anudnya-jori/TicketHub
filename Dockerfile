# One multi-stage Dockerfile for every service.   docker build --build-arg MODULE=booking-service -t tickethub/booking-service .
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY eureka-server/pom.xml eureka-server/pom.xml
COPY config-server/pom.xml config-server/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY event-show-service/pom.xml event-show-service/pom.xml
COPY booking-service/pom.xml booking-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -q -B -pl ${MODULE} -am dependency:go-offline
COPY . .
RUN mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21.0.5_11-jre
ARG MODULE
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
