package com.hify.module.conversation.controller.dto;

import lombok.Data;

/**
 * 创建会话请求.
 */
@Data
public class ChatSessionCreateRequest {

    private String title;

    private Long agentId;

    /**
     * 模型 ID，可选。为空时按以下顺序解析：
     * Agent 绑定的模型 → 第一个可用模型；均无则创建失败。
     */
    private Long modelId;
}
