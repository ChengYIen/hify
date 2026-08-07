package com.hify.module.agent.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具定义响应体（用于工具绑定选择列表）.
 */
@Data
@Builder
public class ToolDefinitionResponse {

    private Long id;
    private String toolName;
    private String toolType;
    private String description;
    private String toolConfig;
    private String status;
    private LocalDateTime createdAt;
}
