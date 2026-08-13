package com.hify.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.knowledge.controller.dto.KnowledgeCreateRequest;
import com.hify.module.knowledge.controller.dto.KnowledgeResponse;
import com.hify.module.knowledge.controller.dto.KnowledgeUpdateRequest;
import com.hify.module.knowledge.repository.ChunkVectorRepository;
import com.hify.module.knowledge.repository.KnowledgeDocumentMapper;
import com.hify.module.knowledge.repository.KnowledgeMapper;
import com.hify.module.knowledge.repository.entity.KnowledgeDocumentEntity;
import com.hify.module.knowledge.repository.entity.KnowledgeEntity;
import com.hify.module.knowledge.service.KnowledgeService;
import com.hify.shared.provider.ModelQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeMapper knowledgeMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final ChunkVectorRepository chunkVectorRepository;
    private final ModelQueryApi modelQueryApi;

    @Override
    public IPage<KnowledgeResponse> page(int page, int size, String name) {
        Page<KnowledgeEntity> p = new Page<>(page, size);
        Page<KnowledgeEntity> result = knowledgeMapper.selectPage(p,
                new LambdaQueryWrapper<KnowledgeEntity>()
                        .like(name != null && !name.isBlank(), KnowledgeEntity::getName, name)
                        .orderByDesc(KnowledgeEntity::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public KnowledgeResponse getById(Long id) {
        KnowledgeEntity entity = knowledgeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeResponse create(KnowledgeCreateRequest request, Long userId) {
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setEnabled(1);
        // 客户端未指定 Embedding 模型时，自动选用第一个可用的 EMBEDDING 模型
        Long embeddingModelId = request.getEmbeddingModelId();
        if (embeddingModelId == null) {
            embeddingModelId = modelQueryApi.getFirstEnabledEmbeddingModelId();
            if (embeddingModelId == null) {
                throw new BizException(ErrorCode.KNOWLEDGE_CONFIG_INVALID,
                        "未指定 Embedding 模型且系统无可用 Embedding 模型，请在提供商配置中添加");
            }
        }
        entity.setEmbeddingModelId(embeddingModelId);
        entity.setDocCount(0);
        entity.setChunkCount(0);
        entity.setCreatedBy(userId);
        knowledgeMapper.insert(entity);
        log.info("Knowledge 创建成功: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeResponse update(Long id, KnowledgeUpdateRequest request) {
        KnowledgeEntity entity = knowledgeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + id);
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled() ? 1 : 0);
        }
        knowledgeMapper.updateById(entity);
        log.info("Knowledge 更新成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeEntity entity = knowledgeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + id);
        }
        knowledgeMapper.deleteById(id);
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                .eq(KnowledgeDocumentEntity::getKnowledgeId, id));
        chunkVectorRepository.logicalDeleteByKnowledge(id);
        log.info("Knowledge 删除成功: id={}", id);
    }

    private KnowledgeResponse toResponse(KnowledgeEntity entity) {
        return KnowledgeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .docCount(entity.getDocCount())
                .chunkCount(entity.getChunkCount())
                .embeddingModelId(entity.getEmbeddingModelId())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
