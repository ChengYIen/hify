package com.hify.module.mcp.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MCP 工具实体，对应表 {@code hify_mcp_tool}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_mcp_tool")
public class McpToolEntity extends BaseEntity {

    /** 所属 MCP Server ID */
    private Long mcpServerId;

    /** 工具名称 */
    private String toolName;

    /** 工具描述 */
    private String description;

    /** 工具入参 JSON Schema */
    private String inputSchema;
}
