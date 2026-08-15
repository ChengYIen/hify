package com.hify.module.mcp.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.shared.tool.dto.AgentBoundToolDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MCP 工具 Mapper.
 */
@Mapper
public interface McpToolMapper extends BaseMapper<McpToolEntity> {

    /**
     * 查询指定 MCP Server 的全部工具（包含逻辑删除行，用于幂等同步）.
     */
    @Select("""
            SELECT id, mcp_server_id, tool_name, description, input_schema,
                   created_at, updated_at, deleted
            FROM hify_mcp_tool
            WHERE mcp_server_id = #{mcpServerId}
            """)
    List<McpToolEntity> selectAllByServerId(@Param("mcpServerId") Long mcpServerId);

    /**
     * 恢复逻辑删除的工具并更新描述/入参 Schema，保持 tool id 不变.
     */
    @Update("""
            UPDATE hify_mcp_tool
            SET description = #{entity.description},
                input_schema = #{entity.inputSchema},
                deleted = 0,
                updated_at = NOW()
            WHERE id = #{entity.id}
            """)
    int restoreAndUpdate(@Param("entity") McpToolEntity entity);

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
