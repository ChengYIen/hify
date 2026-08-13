package com.hify.module.knowledge.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.knowledge.repository.ChunkVectorRepository;
import com.hify.module.knowledge.repository.KnowledgeMapper;
import com.hify.module.knowledge.repository.entity.KnowledgeEntity;
import com.hify.module.knowledge.service.EmbeddingService;
import com.hify.shared.rag.RagRetrievalApi;
import com.hify.shared.rag.dto.RagChunkDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库检索接口实现 —— 供 conversation / agent 跨模块调用.
 * <p>
 * 检索链路：问题 → Embedding（带缓存）→ pgvector 余弦相似度 top-K。
 * 调用方只注入 {@link RagRetrievalApi}，不感知向量库细节。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalApiImpl implements RagRetrievalApi {

    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingService embeddingService;
    private final ChunkVectorRepository chunkVectorRepository;

    @Override
    public List<RagChunkDTO> search(Long knowledgeId, String query, int topK) {
        KnowledgeEntity knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + knowledgeId);
        }
        if (knowledge.getEnabled() != null && knowledge.getEnabled() == 0) {
            log.info("知识库已禁用，跳过检索: knowledgeId={}", knowledgeId);
            return List.of();
        }
        if (knowledge.getEmbeddingModelId() == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_CONFIG_INVALID,
                    "知识库未配置 Embedding 模型: id=" + knowledgeId);
        }
        if (topK <= 0) {
            topK = 5;
        }
        // 问题向量化（缓存命中则跳过 LLM 调用）
        String queryVector = embeddingService.embed(knowledge.getEmbeddingModelId(), query);
        List<RagChunkDTO> chunks = chunkVectorRepository.searchTopK(knowledgeId, queryVector, topK);
        log.info("知识检索完成: knowledgeId={}, topK={}, hits={}", knowledgeId, topK, chunks.size());
        return chunks;
    }
}
