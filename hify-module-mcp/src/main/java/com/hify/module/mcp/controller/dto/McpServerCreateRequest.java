package com.hify.module.mcp.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建 MCP Server 请求.
 */
@Data
public class McpServerCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "endpoint 不能为空")
    private String endpoint;

    /** 是否启用，默认 true */
    private Boolean enabled;
}
