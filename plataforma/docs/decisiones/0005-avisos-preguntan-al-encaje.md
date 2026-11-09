# 0005 Los avisos preguntan al servicio de encaje por REST

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

Para avisar a alguien hay que saber si cada ayuda nueva encaja con su negocio. Las reglas del
encaje ya existen en tres sitios (la web, alertas y `servicio-encaje`) y se mantienen iguales con
una prueba de contrato de 3.600 casos. Una cuarta copia en `servicio-avisos` sería otra cosa que
mantener sincronizada.

Otra opción era que el servicio de encaje conociera los perfiles de todos los suscriptores y
publicara «encaje.encontrado» en Kafka. Pero entonces los datos personales (email aparte, el perfil
del negocio) vivirían en dos servicios, y el encaje tendría que escuchar los cambios de cada
suscripción.

## Decisión

- `servicio-avisos` es el único dueño de las suscripciones.
- En la pasada diaria pregunta a `servicio-encaje` por REST (`POST /api/v1/encajes`) **una vez por
  persona**, con el mismo perfil que el formulario de la web, y usa la respuesta para repartir y
  para volver a comprobar antes de enviar.
- Las convocatorias leídas le llegan por Kafka (`convocatorias.leidas`), como al encaje: cada
  servicio guarda lo suyo y la pasada no depende de que otro servicio le cuente qué es nuevo.
- Si el encaje no responde (tras un reintento), la pasada se para sin marcar nada como enviado y se
  repite en la siguiente.

## Consecuencias

- Las reglas del encaje viven en un solo servicio de la plataforma.
- Hay una dependencia síncrona a la hora de la pasada: con pocos cientos de suscriptores son pocos
  cientos de llamadas al día. Si creciera mucho, el encaje podría aceptar varios perfiles en una
  llamada, o pasar a eventos.
- Probado en `AvisosIntegracionTest` con el cliente HTTP de verdad contra un encaje simulado, y a
  mano con los cinco servicios en `docker compose`.
