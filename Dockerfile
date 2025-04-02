FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
#ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]