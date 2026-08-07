package com.hify.module.agent.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具响应体.
 */
@Data
@Builder
public class AgentToolResponse {

    private Long id;
    private Long agentId;
    private String toolName;
    private String toolType;
    private String toolConfig;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
