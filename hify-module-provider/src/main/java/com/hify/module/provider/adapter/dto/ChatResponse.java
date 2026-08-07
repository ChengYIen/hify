package com.hify.module.provider.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO —— ProviderAdapter 层的统一出参.
 *
 * <p>各适配器实现负责将厂商 HTTP 响应解析为本 DTO，
 * 上层调用方（conversation 模块）只消费统一格式，不感知厂商差异。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 模型返回的文本内容 */
    private String content;

    /** 实际使用的模型名称 */
    private String model;

    /** 停止原因：stop / length / tool_calls / content_filter / error */
    private String finishReason;

    /** Token 用量 */
    private TokenUsage tokenUsage;

    /** 工具调用请求（JSON 数组，模型要求调用工具时） */
    private String toolCalls;

    /** 响应耗时（毫秒） */
    private Long latencyMs;

    // ----------------------------------------------------------------
    // 内嵌类
    // ----------------------------------------------------------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
