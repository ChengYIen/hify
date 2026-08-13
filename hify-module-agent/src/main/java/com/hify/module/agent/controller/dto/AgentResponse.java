package com.hify.module.agent.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 响应体.
 */
@Data
@Builder
public class AgentResponse {

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
    private List<AgentToolResponse> tools;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
