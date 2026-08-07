package com.hify.module.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import com.hify.module.conversation.repository.ChatMessageMapper;
import com.hify.module.conversation.repository.ChatSessionMapper;
import com.hify.module.conversation.repository.entity.ChatMessageEntity;
import com.hify.module.conversation.repository.entity.ChatSessionEntity;
import com.hify.module.conversation.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话消息业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    @Override
    public IPage<ChatMessageResponse> pageBySession(Long sessionId, int page, int pageSize) {
        Page<ChatMessageEntity> p = new Page<>(page, pageSize);
        Page<ChatMessageEntity> result = chatMessageMapper.selectPage(p,
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByAsc(ChatMessageEntity::getSeq));
        return result.convert(this::toResponse);
    }

    @Override
    public List<ChatMessageResponse> listBySession(Long sessionId, int limit) {
        Page<ChatMessageEntity> p = new Page<>(1, limit);
        Page<ChatMessageEntity> result = chatMessageMapper.selectPage(p,
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ChatMessageEntity::getSeq));
        return result.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResponse createUserMessage(Long sessionId, String content) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "sessionId=" + sessionId);
        }

        int nextSeq = session.getMessageCount() + 1;

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setContent(content);
        entity.setSeq(nextSeq);
        chatMessageMapper.insert(entity);

        // 更新会话统计
        session.setMessageCount(nextSeq);
        chatSessionMapper.updateById(session);

        log.info("用户消息创建成功: sessionId={}, seq={}", sessionId, nextSeq);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageResponse createAssistantMessage(Long sessionId, String content, String model,
                                                       String tokenUsage, String finishReason) {
        ChatSessionEntity session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "sessionId=" + sessionId);
        }

        int nextSeq = session.getMessageCount() + 1;

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setModel(model);
        entity.setTokenUsage(tokenUsage);
        entity.setFinishReason(finishReason);
        entity.setSeq(nextSeq);
        chatMessageMapper.insert(entity);

        // 更新会话统计
        session.setMessageCount(nextSeq);
        chatSessionMapper.updateById(session);

        log.info("AI 消息创建成功: sessionId={}, seq={}", sessionId, nextSeq);
        return toResponse(entity);
    }

    @Override
    public ChatMessageResponse getById(Long id) {
        ChatMessageEntity entity = chatMessageMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在: id=" + id);
        }
        return toResponse(entity);
    }

    private ChatMessageResponse toResponse(ChatMessageEntity entity) {
        return ChatMessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .model(entity.getModel())
                .finishReason(entity.getFinishReason())
                .toolCalls(entity.getToolCalls())
                .toolCallId(entity.getToolCallId())
                .fallback(entity.getFallback())
                .seq(entity.getSeq())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
