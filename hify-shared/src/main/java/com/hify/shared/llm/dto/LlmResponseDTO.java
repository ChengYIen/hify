package com.hify.shared.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 统一响应 DTO.
 * <p>
 * 各 provider 实现负责将厂商 API 响应转换为此 DTO，
 * 业务层（conversation 模块）不感知底层是 OpenAI 还是 Claude。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponseDTO {

    /** 模型返回的文本内容 */
    private String content;

    /** 模型名称（实际使用的，可能已被降级路由替换） */
    private String model;

    /** 所属提供商实例 ID，供调用链日志和用量统计使用 */
    private Long providerId;

    /** Token 用量 */
    private TokenUsage usage;

    /** 停止原因：stop / length / tool_calls / content_filter */
    private String finishReason;

    /** 工具调用请求（若模型要求调用工具） */
    private String toolCalls;

    /** 是否为降级模型返回的结果 */
    private boolean fallback;

    /** 响应耗时（毫秒），由 provider 模块从底层透传，供会话落库 */
    private Long latencyMs;

    // ----------------------------------------------------
    // 内嵌类
    // ----------------------------------------------------

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
