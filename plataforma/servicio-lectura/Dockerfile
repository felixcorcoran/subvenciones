# Se construye desde plataforma/: docker build -f servicio-lectura/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Todos los pom (Maven tiene que ver todos los módulos del proyecto padre); el código, solo el que hace falta
COPY seguridad/pom.xml seguridad/
COPY observabilidad/pom.xml observabilidad/
COPY servicio-encaje/pom.xml servicio-encaje/
COPY servicio-recogida/pom.xml servicio-recogida/
COPY servicio-lectura/pom.xml servicio-lectura/
COPY servicio-avisos/pom.xml servicio-avisos/
COPY servicio-preguntas/pom.xml servicio-preguntas/
COPY pasarela/pom.xml pasarela/
RUN sh mvnw -B -q -pl servicio-lectura -am dependency:go-offline
COPY seguridad/src seguridad/src
COPY observabilidad/src observabilidad/src
COPY servicio-lectura/src servicio-lectura/src
RUN sh mvnw -B -q -pl servicio-lectura -am package -DskipTests \
 && java -Djarmode=tools -jar servicio-lectura/target/servicio-lectura-*.jar extract --layers --launcher --destination /extraido

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
# Una capa por ritmo de cambio: las dependencias cambian poco; la aplicación, en cada commit
COPY --from=build /extraido/dependencies/ ./
COPY --from=build /extraido/spring-boot-loader/ ./
COPY --from=build /extraido/snapshot-dependencies/ ./
COPY --from=build /extraido/application/ ./
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
