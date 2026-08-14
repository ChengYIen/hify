package com.hify.module.mcp.service;

import com.hify.module.mcp.controller.dto.McpToolResponse;

import java.util.List;
import java.util.Map;

/**
 * MCP 客户端调用服务.
 */
public interface McpClientService {

    String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments);

    List<String> listTools(Long mcpServerId);

    List<McpToolResponse> listToolResponses(Long mcpServerId);
}
