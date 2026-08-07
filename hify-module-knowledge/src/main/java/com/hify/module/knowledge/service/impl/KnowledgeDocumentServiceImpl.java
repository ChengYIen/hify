package com.hify.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;
import com.hify.module.knowledge.repository.KnowledgeDocumentMapper;
import com.hify.module.knowledge.repository.KnowledgeMapper;
import com.hify.module.knowledge.repository.entity.KnowledgeDocumentEntity;
import com.hify.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库文档业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeMapper knowledgeMapper;

    @Override
    public IPage<KnowledgeDocumentResponse> pageByKnowledge(Long knowledgeId, int page, int pageSize) {
        Page<KnowledgeDocumentEntity> p = new Page<>(page, pageSize);
        Page<KnowledgeDocumentEntity> result = knowledgeDocumentMapper.selectPage(p,
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .eq(KnowledgeDocumentEntity::getKnowledgeId, knowledgeId)
                        .orderByDesc(KnowledgeDocumentEntity::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public KnowledgeDocumentResponse getById(Long id) {
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentResponse create(Long knowledgeId, String filename, String fileType,
                                             Long fileSize, String fileUrl) {
        if (knowledgeMapper.selectById(knowledgeId) == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND, "id=" + knowledgeId);
        }
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKnowledgeId(knowledgeId);
        entity.setFilename(filename);
        entity.setFileType(fileType);
        entity.setFileSize(fileSize != null ? fileSize : 0L);
        entity.setFileUrl(fileUrl);
        entity.setParseStatus("PENDING");
        entity.setChunkCount(0);
        knowledgeDocumentMapper.insert(entity);
        log.info("KnowledgeDocument 创建成功: id={}, filename={}", entity.getId(), filename);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeDocumentEntity entity = knowledgeDocumentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.DOCUMENT_NOT_FOUND, "id=" + id);
        }
        knowledgeDocumentMapper.deleteById(id);
        log.info("KnowledgeDocument 删除成功: id={}", id);
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocumentEntity entity) {
        return KnowledgeDocumentResponse.builder()
                .id(entity.getId())
                .knowledgeId(entity.getKnowledgeId())
                .filename(entity.getFilename())
                .fileType(entity.getFileType())
                .fileSize(entity.getFileSize())
                .fileUrl(entity.getFileUrl())
                .parseStatus(entity.getParseStatus())
                .chunkCount(entity.getChunkCount())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
