# Se construye desde plataforma/: docker build -f servicio-encaje/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY servicio-encaje/pom.xml servicio-encaje/
COPY servicio-recogida/pom.xml servicio-recogida/
COPY servicio-lectura/pom.xml servicio-lectura/
COPY servicio-avisos/pom.xml servicio-avisos/
COPY servicio-preguntas/pom.xml servicio-preguntas/
RUN sh mvnw -B -q -pl servicio-encaje -am dependency:go-offline
COPY servicio-encaje/src servicio-encaje/src
RUN sh mvnw -B -q -pl servicio-encaje -am package -DskipTests \
 && java -Djarmode=tools -jar servicio-encaje/target/servicio-encaje-*.jar extract --layers --launcher --destination /extraido

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
# Una capa por ritmo de cambio: las dependencias cambian poco; la aplicación, en cada commit
COPY --from=build /extraido/dependencies/ ./
COPY --from=build /extraido/spring-boot-loader/ ./
COPY --from=build /extraido/snapshot-dependencies/ ./
COPY --from=build /extraido/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
