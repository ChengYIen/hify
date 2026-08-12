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
    /** Agent 名称（列表展示用，Agent 已删/禁用时为 null） */
    private String agentName;
    private Long modelId;
    private String status;
    private Integer messageCount;
    private Integer totalTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
