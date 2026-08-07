package com.hify.module.conversation.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话会话响应体.
 */
@Data
@Builder
public class ChatSessionResponse {

    private Long id;
    private String title;
    private Long userId;
    private Long agentId;
    private Long modelId;
    private String status;
    private Integer messageCount;
    private Integer totalTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
