package com.hify.module.conversation.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.conversation.controller.dto.ChatSessionCreateRequest;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;

/**
 * 对话会话业务接口.
 */
public interface ChatSessionService {

    IPage<ChatSessionResponse> pageByUser(Long userId, int page, int pageSize);

    ChatSessionResponse getById(Long id);

    ChatSessionResponse create(ChatSessionCreateRequest request, Long userId);

    ChatSessionResponse archive(Long id);

    void delete(Long id);
}
