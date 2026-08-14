package com.hify.module.mcp.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.shared.tool.dto.AgentBoundToolDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * MCP 工具 Mapper.
 */
@Mapper
public interface McpToolMapper extends BaseMapper<McpToolEntity> {

    /**
     * 统计该 MCP Server 下被 Agent 绑定的工具数量.
     */
    @Select("""
            SELECT COUNT(*)
            FROM hify_agent_tool at
            INNER JOIN hify_mcp_tool mt
                ON at.tool_id = mt.id
            WHERE mt.mcp_server_id = #{mcpServerId}
              AND at.deleted = 0
              AND mt.deleted = 0
            """)
    long countAgentBindings(@Param("mcpServerId") Long mcpServerId);

    /**
     * 查询 Agent 绑定的 MCP 工具（name / description / inputSchema / mcpServerId）.
     */
    @Select("""
            SELECT mt.tool_name AS toolName,
                   mt.description AS description,
                   mt.input_schema AS inputSchema,
                   mt.mcp_server_id AS mcpServerId
            FROM hify_agent_tool at
            INNER JOIN hify_mcp_tool mt
                ON at.tool_id = mt.id
            WHERE at.agent_id = #{agentId}
              AND at.deleted = 0
              AND mt.deleted = 0
            ORDER BY at.id
            """)
    List<AgentBoundToolDTO> selectBoundTools(@Param("agentId") Long agentId);
}
