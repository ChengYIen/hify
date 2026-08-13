package com.hify.module.agent.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 列表响应体（轻量级，不含 tools 完整列表）.
 */
@Data
@Builder
public class AgentListResponse {

    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private String systemPrompt;
    private Long modelConfigId;
    private Long workflowId;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer maxContextTurns;
    private Integer toolsEnabled;
    private String knowledgeIds;
    private String status;
    private Long createdBy;
    private Integer toolCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
