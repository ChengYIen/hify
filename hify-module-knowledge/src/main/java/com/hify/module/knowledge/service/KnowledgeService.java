package com.hify.module.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.knowledge.controller.dto.KnowledgeCreateRequest;
import com.hify.module.knowledge.controller.dto.KnowledgeResponse;
import com.hify.module.knowledge.controller.dto.KnowledgeUpdateRequest;

/**
 * 知识库业务接口.
 */
public interface KnowledgeService {

    IPage<KnowledgeResponse> page(int page, int size, String name);

    KnowledgeResponse getById(Long id);

    KnowledgeResponse create(KnowledgeCreateRequest request, Long userId);

    KnowledgeResponse update(Long id, KnowledgeUpdateRequest request);

    void delete(Long id);
}
