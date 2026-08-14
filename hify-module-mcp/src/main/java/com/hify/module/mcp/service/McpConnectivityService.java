package com.hify.module.mcp.service;

import com.hify.module.mcp.controller.dto.ConnectionTestResult;

/**
 * MCP Server 连通性测试业务接口.
 */
public interface McpConnectivityService {

    ConnectionTestResult testConnection(Long mcpServerId);
}
