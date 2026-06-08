FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai
ENV SERVER_PORT=9527
ENV SERVER_SERVLET_CONTEXT_PATH=/seatunnel-web-api
ENV SPRING_DATASOURCE_URL="jdbc:mysql://mariadb.example.local:3306/seatunnel_web?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
ENV SPRING_DATASOURCE_USERNAME=seatunnel_web
ENV SPRING_DATASOURCE_PASSWORD=change_me
ENV SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver

WORKDIR /opt/seatunnel-web

ARG JAR_FILE=seatunnel-web-api/target/seatunnel-web-api.jar
COPY ${JAR_FILE} app.jar

EXPOSE 9527

ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms512m", "-Xmx2g", "-jar", "app.jar"]
