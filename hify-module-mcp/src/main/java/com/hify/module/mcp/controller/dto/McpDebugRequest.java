package com.hify.module.mcp.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * MCP tool debug request.
 */
@Data
public class McpDebugRequest {

    @NotBlank(message = "工具名称不能为空")
    private String toolName;

    private Map<String, Object> arguments = Map.of();
}
