-- Cada convocatoria que conoce la lectura:
--   datos    la convocatoria tal como la publica la recogida (se reenvía tal cual con lo leído)
--   lectura  lo que necesita la lectura y cómo va (lo leído, intentos, errores…)
CREATE TABLE lecturas (
    codigo      TEXT PRIMARY KEY,
    datos       JSONB,
    lectura     JSONB,
    actualizada TIMESTAMPTZ NOT NULL
);

-- Los eventos de Kafka ya aplicados: la misma entrega repetida no se aplica dos veces
CREATE TABLE eventos_procesados (
    id        UUID PRIMARY KEY,
    procesado TIMESTAMPTZ NOT NULL
);

-- Outbox: «convocatoria.leida» se anota en la misma transacción que lo leído
CREATE TABLE outbox (
    orden     BIGSERIAL PRIMARY KEY,
    id        UUID        NOT NULL UNIQUE,
    topico    TEXT        NOT NULL,
    clave     TEXT        NOT NULL,
    carga     JSONB       NOT NULL,
    creado    TIMESTAMPTZ NOT NULL,
    publicado TIMESTAMPTZ
);

CREATE INDEX outbox_pendientes_idx ON outbox (orden) WHERE publicado IS NULL;
