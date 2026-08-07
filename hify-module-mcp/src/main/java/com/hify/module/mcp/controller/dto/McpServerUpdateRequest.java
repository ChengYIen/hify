package com.hify.module.mcp.controller.dto;

import lombok.Data;

/**
 * 更新 MCP 服务请求.
 */
@Data
public class McpServerUpdateRequest {

    private String name;

    private String description;

    private String transport;

    private String command;

    private String args;

    private String envVars;

    private String url;

    private String headers;

    private Integer timeoutMs;

    private String status;
}
