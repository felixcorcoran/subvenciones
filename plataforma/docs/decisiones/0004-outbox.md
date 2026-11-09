# 0004 Outbox transaccional para publicar eventos

- Estado: aceptada
- Fecha: 2026-11-09

## Contexto

La recogida guarda cada convocatoria en su base de datos y tiene que contarlo en Kafka. Hacer las
dos cosas por separado falla de dos maneras: si se guarda y el servicio cae antes de publicar, el
evento se pierde y el resto de servicios no se entera nunca; si se publica y luego falla el
guardado, se anuncia algo que no existe. Kafka y PostgreSQL no comparten transacción.

## Decisión

- El evento se escribe en la tabla `outbox` **en la misma transacción** que la convocatoria. O
  quedan las dos cosas, o ninguna.
- Un relevo (`RelevoOutbox`, cada segundo) lee lo pendiente en orden, lo envía a Kafka esperando
  la confirmación de cada mensaje (`acks=all`, productor idempotente) y lo marca como publicado.
- `FOR UPDATE SKIP LOCKED`: con varias réplicas, dos relevos no publican lo mismo a la vez.
- Si un envío falla, la transacción del lote se deshace y se reintenta en la siguiente vuelta. Lo ya
  enviado de ese lote sale otra vez: la entrega es «al menos una vez», y los consumidores descartan
  los repetidos por el `id` del evento ([0003](0003-eventos-kafka.md)).
- Lo publicado se borra a la semana.

Se descartó Debezium (CDC sobre el registro de PostgreSQL): resuelve lo mismo sin sondeo, pero
añade Kafka Connect y otra pieza que operar. Con decenas de eventos al día, sondear cada segundo
basta, y el patrón es el mismo si un día hace falta cambiarlo.

## Consecuencias

- Ningún evento se pierde aunque el servicio caiga a mitad de pasada; como mucho sale repetido.
- Hasta un segundo de retraso entre guardar y publicar.
- Probado en `RecogidaIntegracionTest` con PostgreSQL y Kafka de verdad, y a mano contra la BDNS
  real: 300 convocatorias encontradas, 118 guardadas y 118 eventos en el tópico, sin pendientes.
