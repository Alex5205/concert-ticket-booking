# --- Этап 1: Сборка приложения (Build stage) ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Сначала копируем только pom.xml и скачиваем зависимости (кэшируется Docker-ом)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код и собираем исполняемый JAR (без повторного прогона тестов)
COPY src ./src
RUN mvn clean package -DskipTests -B

# --- Этап 2: Финальный лёгкий образ для запуска (Runtime stage) ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаём непривилегированного пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем только готовый JAR из первого этапа
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
