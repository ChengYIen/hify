package com.hify.module.mcp.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建 MCP 服务请求.
 */
@Data
public class McpServerCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "传输协议不能为空")
    private String transport;

    private String command;

    private String args;

    private String envVars;

    private String url;

    private String headers;

    @NotNull(message = "超时时间不能为空")
    private Integer timeoutMs;
}
