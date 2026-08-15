package com.hify.module.mcp.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * MCP tool debug response.
 */
@Data
@Builder
public class McpDebugResponse {

    private String result;

    private long elapsedMs;
}
