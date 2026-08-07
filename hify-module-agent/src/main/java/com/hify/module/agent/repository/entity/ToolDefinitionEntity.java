package com.hify.module.agent.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具定义实体（可复用工具目录）.
 * <p>
 * 对应表 {@code hify_tool_definition}。工具在此表中统一定义，
 * Agent 通过 {@code hify_agent_tool} 引用工具 ID 完成绑定。
 * 工具类型支持：MCP / BUILTIN / HTTP。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_tool_definition")
public class ToolDefinitionEntity extends BaseEntity {

    /** 工具名称 */
    private String toolName;

    /** 工具类型：MCP / BUILTIN / HTTP */
    private String toolType;

    /** 工具描述 */
    private String description;

    /** 工具配置（JSON，含参数描述、端点等） */
    private String toolConfig;

    /** 状态：ENABLED / DISABLED */
    private String status;
}
