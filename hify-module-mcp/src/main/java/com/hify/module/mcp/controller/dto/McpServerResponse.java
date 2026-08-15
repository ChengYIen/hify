package com.hify.module.mcp.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP Server 响应体.
 */
@Data
@Builder
public class McpServerResponse {

    private Long id;
    private String name;
    private String description;
    private String endpoint;
    private Boolean enabled;
    private Integer toolCount;
    private List<McpToolResponse> tools;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
