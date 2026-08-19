-- Hify vector schema for PostgreSQL + pgvector.
-- Run this script against the external PG database before starting Hify.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_chunk (
    id           BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT      NOT NULL,
    document_id  BIGINT      NOT NULL,
    chunk_index  INTEGER     NOT NULL,
    content      TEXT        NOT NULL,
    embedding    VECTOR(1536) NOT NULL,
    deleted      SMALLINT    NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_knowledge_document
    ON document_chunk (knowledge_id, document_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_knowledge_deleted
    ON document_chunk (knowledge_id, deleted);
