package com.hify.shared.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 配置 DTO（跨模块共享）.
 * <p>
 * conversation 引擎通过此 DTO 获取 Agent 的运行时配置。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfigDTO {

    private Long id;
    private String name;
    private String systemPrompt;
    private Long modelId;
    /** 绑定的工作流 ID，非空时对话直接走工作流执行 */
    private Long workflowId;
    private Double temperature;
    private Integer maxTokens;
    private Integer maxIterations;
    private Boolean toolsEnabled;
    /** 关联知识库 ID 列表（JSON 数组字符串，如 [1,2]） */
    private String knowledgeIds;
}
