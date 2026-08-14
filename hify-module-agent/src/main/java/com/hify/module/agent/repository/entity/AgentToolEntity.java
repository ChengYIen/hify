package com.hify.module.agent.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 工具关联实体，对应表 {@code hify_agent_tool}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_agent_tool")
public class AgentToolEntity extends BaseEntity {

    /** Agent ID（关联 hify_agent.id） */
    private Long agentId;

    /** 工具 ID（关联 hify_mcp_tool.id） */
    private Long toolId;
}
