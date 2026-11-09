-- Las convocatorias que conoce el servicio: entera en JSONB y la fecha de cierre en su columna
CREATE TABLE convocatorias (
    codigo      TEXT PRIMARY KEY,
    cierre      DATE,
    documento   JSONB       NOT NULL,
    actualizada TIMESTAMPTZ NOT NULL
);

CREATE INDEX convocatorias_cierre_idx ON convocatorias (cierre);
