-- =====================================================================
-- Hify 向量库 Schema（PostgreSQL + pgvector）
-- 仅 knowledge 模块使用：知识库文档块 + 向量。业务数据全部走 MySQL。
-- 需先执行：CREATE EXTENSION IF NOT EXISTS vector;
-- 向量维度固定为 1536（text-embedding-3-small / ada-002），切换模型需重建表。
-- =====================================================================

CREATE TABLE IF NOT EXISTS document_chunk (
    id           BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT      NOT NULL,                       -- 对应 MySQL hify_knowledge.id
    document_id  BIGINT      NOT NULL,                       -- 对应 MySQL hify_knowledge_document.id
    chunk_index  INTEGER     NOT NULL,                       -- 块序号（文档内从 0 开始）
    content      TEXT        NOT NULL,                       -- 原文
    embedding    VECTOR(1536) NOT NULL,                      -- OpenAI embedding 向量
    deleted      SMALLINT    NOT NULL DEFAULT 0,             -- 逻辑删除 0=未删 1=已删
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- HNSW 余弦相似度索引（构建略慢但查询性能优于 IVFFlat，CLAUDE.md §13.2）
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_embedding
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 按知识库+文档定位块（删除/重索引/分页）
CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_knowledge_document
    ON document_chunk (knowledge_id, document_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunk_knowledge_deleted
    ON document_chunk (knowledge_id, deleted);
