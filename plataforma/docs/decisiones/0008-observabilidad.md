# 0008 Observabilidad con OpenTelemetry, y trazas que cruzan la outbox

- Estado: aceptada
- Fecha: 2026-11-10

## Contexto

Una convocatoria pasa por cuatro servicios y dos tópicos de Kafka antes de llegar a un correo.
Cuando algo va lento o se pierde, hay que poder seguirla de un servicio a otro sin ir juntando logs
a mano. Y hay un corte natural: los eventos no se publican en la petición que los crea, sino más
tarde y en otro hilo (la outbox, [0004](0004-outbox.md)).

## Decisión

- **OpenTelemetry por OTLP** con el starter de Spring Boot: trazas y métricas de Micrometer. La
  configuración es común (módulo `observabilidad`) y cada servicio solo recibe a dónde enviar, con
  la variable estándar `OTEL_EXPORTER_OTLP_ENDPOINT`. Sin ella no se exporta nada: ni las pruebas ni
  un arranque suelto llenan el log de errores de conexión.
- **Trazas en Kafka:** productor y consumidor observados; el `traceparent` viaja en las cabeceras
  del mensaje.
- **La traza cruza la outbox:** al anotar un evento se guarda el `traceparent` de la traza en curso
  en la fila de la outbox; el relevo, al publicar, abre un span hijo de aquel contexto. El envío a
  Kafka queda dentro de la traza de la petición que lo originó.
- **Sin ruido:** no se trazan las comprobaciones de salud, el sondeo del relevo cada segundo ni los
  filtros de Spring Security. En la primera prueba real tapaban todo lo demás.
- **Muestreo del 100 %:** hay poco tráfico y se quiere ver todo. Con más tráfico se bajaría.
- **Los logs** llevan el id de traza en cada línea; no se exportan por OTLP.
- En local, `grafana/otel-lgtm`: un contenedor con el colector, Tempo, Prometheus, Loki y Grafana.

## Consecuencias

- Una recogida lanzada por la pasarela se ve en **una sola traza**: pasarela → recogida (con cada
  llamada a la BDNS) → publicación de cada evento → consumo en la lectura. Comprobado con
  `herramientas/prueba-trazas.mjs`.
- La lectura con IA se hace más tarde, en una pasada propia: esa pasada es otra traza. Unirla a la
  de la recogida exigiría guardar el contexto con cada convocatoria pendiente; no se ha hecho.
- Panel y alertas (añadidos después): un panel propio y cuatro alertas por correo, cargados al
  arrancar Grafana desde `infra/grafana`. Para las colas de errores y la outbox hacían falta métricas
  propias. Un contador que aparece por primera vez no tiene muestra anterior y `increase()` no lo
  ve, así que la alerta de las colas de errores lo compara con su valor de hace 15 minutos.
