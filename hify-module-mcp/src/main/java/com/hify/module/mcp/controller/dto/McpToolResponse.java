package com.hify.module.mcp.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具响应体.
 */
@Data
@Builder
public class McpToolResponse {

    private Long id;
    private Long mcpServerId;
    private String toolName;
    private String description;
    private String inputSchema;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
