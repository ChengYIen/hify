package com.hify.module.agent.controller.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新 Agent 请求（仅基本信息，不含工具）.
 * <p>所有字段可选，null 表示不更新该字段。</p>
 * <p>工具更新请使用独立接口 {@code PUT /api/v1/agents/{id}/tools}。</p>
 */
@Data
public class AgentUpdateRequest {

    private String name;

    private String description;

    private String avatarUrl;

    private String systemPrompt;

    private Long modelId;

    private Long workflowId;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer maxIterations;

    private Integer toolsEnabled;

    private String knowledgeIds;

    private String status;
}
