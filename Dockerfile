FROM openjdk:21-ea-1-jdk-slim

WORKDIR /app

COPY target/ping-monitor-skarb-tablet-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]
