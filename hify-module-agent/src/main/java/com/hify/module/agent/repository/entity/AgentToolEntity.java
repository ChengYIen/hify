package com.hify.module.agent.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 工具关联实体.
 * <p>
 * 对应表 {@code hify_agent_tool}。记录 Agent 可调用的工具列表，
 * 支持 MCP 工具、内置工具和 HTTP 工具三种类型。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_agent_tool")
public class AgentToolEntity extends BaseEntity {

    /** Agent ID（关联 hify_agent.id） */
    private Long agentId;

    /** 工具名称 */
    private String toolName;

    /** 工具类型：MCP / BUILTIN / HTTP */
    private String toolType;

    /** 工具配置（JSON，含参数描述、端点等） */
    private String toolConfig;

    /** 排序优先级 */
    private Integer priority;
}
