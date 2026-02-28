FROM eclipse-temurin:21-jre AS builder
WORKDIR /application
ARG JAR_FILE=build/libs/mixer-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --launcher

FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /application/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]