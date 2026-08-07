package com.hify.module.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;

/**
 * 知识库文档业务接口.
 */
public interface KnowledgeDocumentService {

    IPage<KnowledgeDocumentResponse> pageByKnowledge(Long knowledgeId, int page, int pageSize);

    KnowledgeDocumentResponse getById(Long id);

    KnowledgeDocumentResponse create(Long knowledgeId, String filename, String fileType, Long fileSize, String fileUrl);

    void delete(Long id);
}
