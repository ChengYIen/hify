package com.hify.module.agent.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建 Agent 请求.
 */
@Data
public class AgentCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    /** Agent 描述（可选） */
    private String description;

    /** 头像 URL（可选） */
    private String avatarUrl;

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    @NotNull(message = "模型配置 ID 不能为空")
    private Long modelConfigId;

    /** 温度 0.00–2.00 */
    private BigDecimal temperature;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 对话上下文最大轮次 */
    private Integer maxContextTurns;

    /** 是否启用工具调用 */
    private Integer toolsEnabled;

    /** 关联知识库 ID 列表（JSON 数组） */
    private String knowledgeIds;

    /** 状态：ENABLED / DISABLED / DRAFT */
    private String status;

    /** 绑定的工具定义 ID 列表（引用 hify_tool_definition.id，可选） */
    private List<Long> toolIds;
}
