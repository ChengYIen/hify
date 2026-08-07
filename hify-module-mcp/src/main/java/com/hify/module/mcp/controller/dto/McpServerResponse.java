package com.hify.module.mcp.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 服务响应体.
 */
@Data
@Builder
public class McpServerResponse {

    private Long id;
    private String name;
    private String description;
    private String transport;
    private String command;
    private String args;
    private String envVars;
    private String url;
    private String headers;
    private Integer timeoutMs;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
