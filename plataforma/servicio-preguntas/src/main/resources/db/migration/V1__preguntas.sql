-- pgvector: los vectores (embeddings) y la búsqueda por parecido, dentro de PostgreSQL
CREATE EXTENSION IF NOT EXISTS vector;

-- Las bases de cada convocatoria leída y cómo va su índice
CREATE TABLE documentos (
    codigo       VARCHAR(20) PRIMARY KEY,
    titulo       TEXT        NOT NULL,
    documento_id BIGINT,
    nombre       TEXT,
    estado       VARCHAR(10) NOT NULL,
    intentos     INTEGER     NOT NULL DEFAULT 0,
    motivo       TEXT,
    actualizado  TIMESTAMPTZ NOT NULL
);

CREATE INDEX documentos_pendientes ON documentos (actualizado) WHERE estado = 'PENDIENTE';

-- Los pasajes de las bases con su página y su vector (768 dimensiones, las que se piden a Gemini)
CREATE TABLE pasajes (
    codigo VARCHAR(20) NOT NULL REFERENCES documentos (codigo) ON DELETE CASCADE,
    orden  INTEGER     NOT NULL,
    pagina INTEGER     NOT NULL,
    texto  TEXT        NOT NULL,
    vector VECTOR(768) NOT NULL,
    PRIMARY KEY (codigo, orden)
);

-- Búsqueda aproximada por distancia del coseno (HNSW)
CREATE INDEX pasajes_vector ON pasajes USING hnsw (vector vector_cosine_ops);

CREATE TABLE eventos_procesados (
    id        UUID PRIMARY KEY,
    procesado TIMESTAMPTZ NOT NULL
);
