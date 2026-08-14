package com.hify.module.mcp.controller.dto;

import lombok.Data;

/**
 * 更新 MCP Server 请求.
 */
@Data
public class McpServerUpdateRequest {

    private String name;

    private String endpoint;

    private Boolean enabled;
}
