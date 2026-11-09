# 0003 Los servicios se hablan con eventos de Kafka

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

La cadena es recoger → leer con IA → encajar → avisar. Cada paso va a su ritmo: la lectura con IA
depende del cupo gratuito de Gemini y puede tardar días en ponerse al día, y los avisos salen una
vez por la mañana. Si los servicios se llamaran por REST, un paso caído pararía a los demás.

## Decisión

- Los servicios publican hechos en Kafka (KRaft, sin ZooKeeper): `convocatoria.leida` en el
  tópico `convocatorias.leidas`, y los siguientes con el mismo patrón.
- **Clave = código de la convocatoria.** Todos los eventos de una convocatoria van a la misma
  partición y se consumen en orden: una relectura nunca se adelanta a la lectura anterior.
- **Sobre con versión:** `id`, `tipo`, `version`, `ocurrido` y los datos. El contenido es el mismo
  formato que publica la web en `datos/convocatorias.json`, que ya es el contrato que leen alertas
  y la plataforma.
- **Idempotencia en el consumidor.** Kafka entrega «al menos una vez». El consumidor apunta el `id`
  del evento en `eventos_procesados` en la misma transacción que el cambio; una reentrega no hace
  nada. El offset se confirma después de la transacción.
- **Errores:** un fallo pasajero se reintenta con espera creciente (hasta 10 segundos). Un mensaje
  que nunca se podrá aplicar (JSON roto, tipo o versión desconocidos, fecha mal escrita) va directo
  a `convocatorias.leidas.dlt` con la excepción en las cabeceras, y la partición sigue.
- Mensajes en texto JSON (`StringSerializer`) y conversión con Jackson en el adaptador: el dominio
  no sabe nada de Kafka.

## Consecuencias

- Un servicio nuevo, o uno que pierde su base de datos, se reconstruye leyendo el tópico desde el
  principio (`auto-offset-reset: earliest`), si la retención lo permite.
- Hace falta vigilar la cola de errores; en la fase 5 tendrá su métrica y su alerta.
- Los productores tendrán que publicar sin perder eventos aunque caigan entre guardar y publicar:
  para eso, outbox transaccional cuando exista el servicio de recogida.
- Probado con Kafka y PostgreSQL de verdad en contenedores (`KafkaEncajeTest`), y a mano con las
  241 convocatorias reales de la web (`herramientas/eventos-convocatorias.mjs`).
