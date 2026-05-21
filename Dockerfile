# syntax=docker/dockerfile:1

# --- build: 부트 JAR 생성 (테스트는 Testcontainers 필요 → 이미지 빌드 시 제외) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle ./
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

# --- runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker \
    STORAGE_LOCAL_DIR=/data/storage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
