# --- Этап 1: Сборка ---
FROM maven:3.8.5-openjdk-18 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -Dmaven.test.skip=true

# --- Этап 2: Финальный образ ---
FROM openjdk:18-jdk-slim
WORKDIR /app

# Копируем только jar-файл из этапа сборки
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]