-- Las convocatorias guardadas: enteras en JSONB, con lo que se consulta en columnas
CREATE TABLE convocatorias (
    codigo      TEXT PRIMARY KEY,
    documento   JSONB       NOT NULL,
    actualizada TIMESTAMPTZ NOT NULL
);

-- Las vistas y descartadas (concesión directa, cerradas…), para no volver a pedir su ficha
CREATE TABLE descartadas (
    codigo TEXT PRIMARY KEY,
    motivo TEXT NOT NULL,
    fecha  DATE NOT NULL
);

-- Cada pasada con su resumen: de aquí sale el «desde» de la siguiente y lo que se vigila
CREATE TABLE pasadas (
    id      BIGSERIAL PRIMARY KEY,
    fecha   TIMESTAMPTZ NOT NULL,
    resumen JSONB       NOT NULL
);

-- Outbox: los eventos se escriben en la misma transacción que el cambio y otro proceso los
-- publica en Kafka en el orden en que se anotaron
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
