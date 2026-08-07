package com.hify.module.provider.controller.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 连通性测试结果.
 */
@Data
@Builder
public class ConnectionTestResult {

    /** 是否连通成功（HTTP 2xx 且能解析到模型列表） */
    private boolean success;

    /** 响应延迟（毫秒） */
    private long latencyMs;

    /** 模型数量（成功时有效） */
    private int modelCount;

    /** 错误信息（失败时有效） */
    private String errorMessage;
}
