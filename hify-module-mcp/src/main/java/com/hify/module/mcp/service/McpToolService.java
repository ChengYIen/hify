package com.hify.module.mcp.service;

import com.hify.module.mcp.controller.dto.McpToolResponse;

import java.util.List;

/**
 * MCP 工具查询业务接口.
 */
public interface McpToolService {

    /**
     * 返回所有可用工具（所属 MCP Server 已启用）.
     */
    List<McpToolResponse> listAllEnabled();
}
