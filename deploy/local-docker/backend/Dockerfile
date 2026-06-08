FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=seatunnel-web-api/target/*.jar
COPY ${JAR_FILE} /app/seatunnel-web-api.jar

EXPOSE 9527

ENTRYPOINT ["java", "-jar", "/app/seatunnel-web-api.jar"]
