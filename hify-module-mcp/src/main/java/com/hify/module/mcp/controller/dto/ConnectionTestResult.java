package com.hify.module.mcp.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * MCP 连通性测试结果.
 */
@Data
@Builder
public class ConnectionTestResult {

    /** 是否连通成功 */
    private boolean success;

    /** 响应延迟（毫秒） */
    private long latencyMs;

    /** 工具数量（成功时有效） */
    private int toolCount;

    /** 错误信息（失败时有效） */
    private String errorMessage;
}
