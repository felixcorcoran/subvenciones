-- Los eventos de Kafka ya aplicados: la misma entrega repetida no se aplica dos veces
CREATE TABLE eventos_procesados (
    id        UUID PRIMARY KEY,
    procesado TIMESTAMPTZ NOT NULL
);
