package com.hify.module.agent.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 工具关联响应体.
 */
@Data
@Builder
public class AgentToolResponse {

    private Long id;
    private Long agentId;
    private Long toolId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
