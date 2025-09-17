FROM openjdk:18.0-jdk
LABEL authors="Artem"
ARG JAR_FILE=target/TelegramDemoBot-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]