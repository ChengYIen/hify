package com.hify.shared.tool;

import com.hify.shared.tool.dto.AgentBoundToolDTO;

import java.util.Collection;
import java.util.List;

/**
 * MCP 工具查询接口（跨模块契约，由 mcp 模块实现）.
 */
public interface McpToolQueryApi {

    /**
     * 返回给定工具 ID 中「存在且对应 MCP Server 已启用」的工具 ID 列表.
     */
    List<Long> listAvailableToolIds(Collection<Long> toolIds);

    /**
     * Return MCP tools bound to the agent.
     *
     * @param agentId Agent ID
     * @return bound tool definitions (name/description/inputSchema/mcpServerId)
     */
    List<AgentBoundToolDTO> listBoundTools(Long agentId);
}
