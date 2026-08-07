package com.hify.module.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.conversation.controller.dto.ChatSessionCreateRequest;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;
import com.hify.module.conversation.repository.ChatSessionMapper;
import com.hify.module.conversation.repository.entity.ChatSessionEntity;
import com.hify.module.conversation.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话会话业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public IPage<ChatSessionResponse> pageByUser(Long userId, int page, int pageSize) {
        Page<ChatSessionEntity> p = new Page<>(page, pageSize);
        Page<ChatSessionEntity> result = chatSessionMapper.selectPage(p,
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getUserId, userId)
                        .orderByDesc(ChatSessionEntity::getUpdatedAt));
        return result.convert(this::toResponse);
    }

    @Override
    public ChatSessionResponse getById(Long id) {
        ChatSessionEntity entity = chatSessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResponse create(ChatSessionCreateRequest request, Long userId) {
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setTitle(request.getTitle());
        entity.setUserId(userId);
        entity.setAgentId(request.getAgentId());
        entity.setModelId(request.getModelId());
        entity.setStatus("ACTIVE");
        entity.setMessageCount(0);
        entity.setTotalTokens(0);
        chatSessionMapper.insert(entity);
        log.info("ChatSession 创建成功: id={}, userId={}", entity.getId(), userId);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResponse archive(Long id) {
        ChatSessionEntity entity = chatSessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "id=" + id);
        }
        entity.setStatus("ARCHIVED");
        chatSessionMapper.updateById(entity);
        log.info("ChatSession 归档成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ChatSessionEntity entity = chatSessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "id=" + id);
        }
        chatSessionMapper.deleteById(id);
        log.info("ChatSession 删除成功: id={}", id);
    }

    private ChatSessionResponse toResponse(ChatSessionEntity entity) {
        return ChatSessionResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .userId(entity.getUserId())
                .agentId(entity.getAgentId())
                .modelId(entity.getModelId())
                .status(entity.getStatus())
                .messageCount(entity.getMessageCount())
                .totalTokens(entity.getTotalTokens())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
