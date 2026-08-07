package com.hify.module.agent.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent 工具创建/更新请求.
 */
@Data
public class AgentToolRequest {

    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    @NotBlank(message = "工具类型不能为空")
    private String toolType;

    private String toolConfig;

    private Integer priority;
}
