# Se construye desde plataforma/: docker build -f servicio-recogida/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY servicio-encaje/pom.xml servicio-encaje/
COPY servicio-recogida/pom.xml servicio-recogida/
COPY servicio-lectura/pom.xml servicio-lectura/
COPY servicio-avisos/pom.xml servicio-avisos/
RUN sh mvnw -B -q -pl servicio-recogida -am dependency:go-offline
COPY servicio-recogida/src servicio-recogida/src
RUN sh mvnw -B -q -pl servicio-recogida -am package -DskipTests \
 && java -Djarmode=tools -jar servicio-recogida/target/servicio-recogida-*.jar extract --layers --launcher --destination /extraido

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
# Una capa por ritmo de cambio: las dependencias cambian poco; la aplicación, en cada commit
COPY --from=build /extraido/dependencies/ ./
COPY --from=build /extraido/spring-boot-loader/ ./
COPY --from=build /extraido/snapshot-dependencies/ ./
COPY --from=build /extraido/application/ ./
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
