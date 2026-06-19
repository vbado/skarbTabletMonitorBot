FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:21-ea-1-jdk-slim
WORKDIR /app
COPY --from=build /app/target/ping-monitor-skarb-tablet-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]