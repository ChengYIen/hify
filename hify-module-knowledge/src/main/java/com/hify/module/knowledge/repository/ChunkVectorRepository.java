package com.hify.module.knowledge.repository;

import com.hify.module.knowledge.service.dto.ChunkDTO;
import com.hify.shared.rag.dto.RagChunkDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 向量分块仓储 —— PostgreSQL pgvector 数据访问.
 * <p>
 * 向量数据不走 MyBatis-Plus（双数据源分工：MySQL = ORM CRUD，PG = JdbcTemplate 向量查询，
 * 见 {@code DataSourceConfig}）。向量以 pgvector 字面量字符串（如 {@code "[0.1,0.2,...]"}）读写。
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChunkVectorRepository {

    private final @Qualifier("postgresqlJdbcTemplate") JdbcTemplate jdbcTemplate;

    /**
     * 插入一个分块（含向量）.
     *
     * @param knowledgeId 知识库 ID
     * @param documentId  文档 ID
     * @param chunkIndex  块序号
     * @param content     原文
     * @param embedding   向量字面量，如 {@code "[0.1,0.2,...]"}
     */
    public void insertChunk(Long knowledgeId, Long documentId, int chunkIndex,
                            String content, String embedding) {
        jdbcTemplate.update(
                "INSERT INTO document_chunk (knowledge_id, document_id, chunk_index, content, embedding) " +
                        "VALUES (?, ?, ?, ?, ?::vector)",
                knowledgeId, documentId, chunkIndex, content, embedding);
    }

    /**
     * 批量写入向量块（JdbcTemplate.batchUpdate）.
     */
    public void batchInsertChunks(Long knowledgeId, Long documentId, List<ChunkDTO> chunks) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO document_chunk (knowledge_id, document_id, chunk_index, content, embedding) " +
                        "VALUES (?, ?, ?, ?, ?::vector)",
                chunks,
                chunks.size(),
                (ps, chunk) -> {
                    ps.setLong(1, knowledgeId);
                    ps.setLong(2, documentId);
                    ps.setInt(3, chunk.getChunkIndex());
                    ps.setString(4, chunk.getContent());
                    ps.setString(5, chunk.getEmbedding());
                });
    }

    /**
     * 删除某文档的全部向量块（重新索引时幂等清理）.
     */
    public void deleteByDocument(Long documentId) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE document_id = ?", documentId);
    }

    /**
     * 删除某知识库的全部向量块（删除知识库 / 重建索引时使用）.
     */
    public void deleteByKnowledge(Long knowledgeId) {
        jdbcTemplate.update("DELETE FROM document_chunk WHERE knowledge_id = ?", knowledgeId);
    }

    /**
     * 逻辑删除某知识库的全部向量块（删除知识库时使用，检索自动跳过）.
     */
    public void logicalDeleteByKnowledge(Long knowledgeId) {
        jdbcTemplate.update(
                "UPDATE document_chunk SET deleted = 1 WHERE knowledge_id = ? AND deleted = 0",
                knowledgeId);
    }

    /**
     * 逻辑删除某文档的全部向量块（删除文档时使用）.
     */
    public void logicalDeleteByDocument(Long documentId) {
        jdbcTemplate.update(
                "UPDATE document_chunk SET deleted = 1 WHERE document_id = ? AND deleted = 0",
                documentId);
    }

    /**
     * 按文档查询向量块列表（chunk_index 正序）.
     */
    public List<RagChunkDTO> listByDocument(Long documentId) {
        return jdbcTemplate.query(
                        "SELECT id, knowledge_id, document_id, chunk_index, content, created_at " +
                        "FROM document_chunk " +
                        "WHERE document_id = ? AND deleted = 0 " +
                        "ORDER BY chunk_index",
                (rs, rowNum) -> RagChunkDTO.builder()
                        .id(rs.getLong("id"))
                        .knowledgeId(rs.getLong("knowledge_id"))
                        .documentId(rs.getLong("document_id"))
                        .chunkIndex(rs.getInt("chunk_index"))
                        .content(rs.getString("content"))
                        .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                        .build(),
                documentId);
    }

    /**
     * 余弦相似度检索 Top-K.
     *
     * @param knowledgeId    知识库 ID（限定范围）
     * @param queryEmbedding 查询向量字面量
     * @param topK           返回条数
     * @return 最相关分块（按相似度降序，0~1 越大越像）
     */
    public List<RagChunkDTO> searchTopK(Long knowledgeId, String queryEmbedding, int topK) {
        return jdbcTemplate.query(
                "SELECT id, knowledge_id, chunk_index, content, " +
                        "       1 - (embedding <=> ?::vector) AS similarity " +
                        "FROM document_chunk " +
                        "WHERE knowledge_id = ? AND deleted = 0 " +
                        "ORDER BY embedding <=> ?::vector " +
                        "LIMIT ?",
                (rs, rowNum) -> RagChunkDTO.builder()
                        .id(rs.getLong("id"))
                        .knowledgeId(rs.getLong("knowledge_id"))
                        .content(rs.getString("content"))
                        .score(rs.getDouble("similarity"))
                        .build(),
                queryEmbedding, knowledgeId, queryEmbedding, topK);
    }

    /**
     * 统计某知识库的向量块总数.
     */
    public int countByKnowledge(Long knowledgeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk WHERE knowledge_id = ? AND deleted = 0",
                Integer.class, knowledgeId);
        return count != null ? count : 0;
    }
}
