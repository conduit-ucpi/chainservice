FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar
#COPY .env .env

EXPOSE 8978

ENTRYPOINT ["java", "-jar", "app.jar"]
