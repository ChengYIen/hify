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
    private Double temperature;
    private Integer maxTokens;
    private Integer maxIterations;
    private Boolean toolsEnabled;
}
