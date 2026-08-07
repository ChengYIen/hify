package com.hify.module.conversation.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建会话请求.
 */
@Data
public class ChatSessionCreateRequest {

    private String title;

    private Long agentId;

    @NotNull(message = "模型 ID 不能为空")
    private Long modelId;
}
