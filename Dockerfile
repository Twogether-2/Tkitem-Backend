FROM openjdk:21

ARG JAR_FILE_PATH=build/libs/*.jar

WORKDIR /apps

COPY $JAR_FILE_PATH app.jar

CMD ["java", "--enable-preview" ,"-jar", "app.jar"]