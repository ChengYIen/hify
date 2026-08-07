package com.hify.module.conversation.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息响应体.
 */
@Data
@Builder
public class ChatMessageResponse {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String model;
    private String finishReason;
    private String toolCalls;
    private String toolCallId;
    private Integer fallback;
    private Integer seq;
    private LocalDateTime createdAt;
}
