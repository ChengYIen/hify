package com.hify.module.agent.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 详情响应体（含完整工具列表）.
 * <p>
 * 用于创建/详情查询的返回值，包含 Agent 全部字段 + 绑定的工具列表。
 * 与 {@link AgentListResponse} 的区别是本类包含 {@code tools} 列表。
 * </p>
 */
@Data
@Builder
public class AgentDetailResponse {

    private Long id;
    private String name;
    private String description;
    private String avatarUrl;
    private String systemPrompt;
    private Long modelConfigId;
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
