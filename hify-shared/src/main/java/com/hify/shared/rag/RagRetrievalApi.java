package com.hify.shared.rag;

import com.hify.shared.rag.dto.RagChunkDTO;

import java.util.List;

/**
 * 知识库检索接口（跨模块共享）.
 * <p>
 * conversation 或 agent 通过此接口检索知识库，
 * 由 knowledge 模块实现。
 * </p>
 */
public interface RagRetrievalApi {

    /**
     * 向量相似度检索.
     *
     * @param knowledgeId 知识库 ID
     * @param query       查询文本
     * @param topK        返回 Top-K 个最相关块
     * @return 相关文档块列表
     */
    List<RagChunkDTO> search(Long knowledgeId, String query, int topK);
}
