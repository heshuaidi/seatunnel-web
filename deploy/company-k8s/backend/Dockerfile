FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai
ENV SERVER_PORT=8080
ENV SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api

WORKDIR /opt/seatunnel-web

ARG JAR_FILE=seatunnel-web-api/target/seatunnel-web-api.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms512m", "-Xmx2g", "-jar", "app.jar"]
