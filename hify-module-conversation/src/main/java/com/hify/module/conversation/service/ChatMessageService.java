package com.hify.module.conversation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;

import java.util.List;

/**
 * 对话消息业务接口.
 */
public interface ChatMessageService {

    IPage<ChatMessageResponse> pageBySession(Long sessionId, int page, int pageSize);

    List<ChatMessageResponse> listBySession(Long sessionId, int limit);

    ChatMessageResponse createUserMessage(Long sessionId, String content);

    ChatMessageResponse createAssistantMessage(Long sessionId, String content, String model,
                                                String tokenUsage, String finishReason, Integer latencyMs);

    ChatMessageResponse getById(Long id);
}
