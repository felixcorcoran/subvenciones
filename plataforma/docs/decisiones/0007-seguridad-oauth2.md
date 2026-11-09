# 0007 Seguridad: OAuth2 con Keycloak, validada en cada servicio

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

La plataforma tiene dos tipos de rutas. Unas son públicas por diseño: el encaje que usa el
formulario del radar, «pregunta a la convocatoria» y las suscripciones, cuya llave es el token de
256 bits del enlace de cada correo. Otras son de administración: lanzar a mano la recogida, la
lectura, la pasada de avisos o el indexado, y cargar convocatorias. Esas no pueden quedar
abiertas: gastan cupo de la IA, llaman a la BDNS y mandan correos.

## Decisión

- **Keycloak** como proveedor de identidad (OAuth2 / OpenID Connect), con el realm
  `subvenciones` (infra/keycloak): el rol `admin`, el cliente `operaciones` (client credentials,
  con el rol) para tareas automáticas, `solo-lectura` (sin el rol) para las pruebas y `radar-web`
  (público, con PKCE) para el futuro panel.
- **Cada servicio es un resource server** (Spring Security): valida la firma, el emisor y la
  caducidad del JWT con las claves públicas del realm, sin sesiones ni cookies (y por eso sin CSRF).
  Los roles del realm pasan a roles de Spring.
- **Las reglas viven en un módulo común** (`seguridad`, una autoconfiguración de Spring Boot) y cada
  servicio solo declara sus listas: rutas públicas y rutas de administración. Lo que no está en
  ninguna lista pide un usuario autenticado: nada queda abierto por olvido.
- **La pasarela** (Spring Cloud Gateway, Server Web MVC) es la única entrada desde fuera: reparte
  las rutas, atiende CORS para las webs del radar y limita por IP las rutas públicas (sobre todo
  preguntar, que gasta cupo de la IA). **No valida tokens**: los pasa tal cual y cada servicio los
  valida. Así una pasarela mal configurada, o una llamada que no pase por ella, no abre nada.
- El emisor de los tokens es fijo (`KC_HOSTNAME`), y los servicios piden las claves a Keycloak por
  la red interna: el token vale igual se pida desde fuera o desde otro contenedor.

## Consecuencias

- Defensa en profundidad: la pasarela filtra abuso; los servicios deciden quién puede qué.
- Cada servicio depende de las claves del realm; Spring las pide la primera vez que llega un token
  y las guarda, así que Keycloak no tiene que estar arriba para arrancar los servicios.
- El límite por IP vive en la memoria de la pasarela: con varias réplicas habría que llevarlo a un
  almacén compartido (Redis).
- Probado con un Keycloak de verdad en Testcontainers (módulo `seguridad`), con pruebas de cada
  servicio (401 sin token, 403 sin rol) y de punta a punta en `docker compose`
  (`herramientas/prueba-seguridad.mjs`).
