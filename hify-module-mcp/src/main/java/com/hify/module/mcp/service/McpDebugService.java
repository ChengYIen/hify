package com.hify.module.mcp.service;

import com.hify.module.mcp.controller.dto.McpDebugRequest;
import com.hify.module.mcp.controller.dto.McpDebugResponse;

/**
 * MCP tool debug service.
 */
public interface McpDebugService {

    McpDebugResponse debug(Long mcpServerId, McpDebugRequest request);
}
