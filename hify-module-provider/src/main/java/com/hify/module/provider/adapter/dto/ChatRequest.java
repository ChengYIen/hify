package com.hify.module.provider.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 聊天请求 DTO —— ProviderAdapter 层的统一入参.
 *
 * <p>各适配器实现负责将本 DTO 转换为对应厂商的 HTTP 请求格式。
 * 包含传输层配置（baseUrl / apiKey）和业务层参数（model / messages / tools），
 * 一次传入即可完成调用，调用方无需感知厂商差异。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    // ---- 传输层 ----

    /** 提供商 API 基础地址，{@code null} 时使用适配器默认值 */
    private String baseUrl;

    /** API 密钥（明文，调用方从数据库解密后传入） */
    private String apiKey;

    // ---- 请求参数 ----

    /** 模型名称（如 gpt-4o / claude-sonnet-4-20250514） */
    private String model;

    /** 对话消息列表 */
    private List<Message> messages;

    /** 温度 0.0–2.0 */
    private Double temperature;

    /** 最大输出 Token 数 */
    private Integer maxTokens;

    /** 是否流式输出，默认 {@code false} */
    private boolean stream;

    /** 工具定义列表（function calling），{@code null} 或空列表表示不启用工具 */
    private List<ToolDefinition> tools;

    /** 厂商特有参数透传（如 OpenAI 的 top_p / frequency_penalty 等） */
    private Map<String, Object> extra;

    // ----------------------------------------------------------------
    // 内嵌类
    // ----------------------------------------------------------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /** system / user / assistant / tool */
        private String role;

        /** 消息文本内容 */
        private String content;

        /** 模型返回的工具调用请求（role=assistant 时使用） */
        private List<ToolCall> toolCalls;

        /** 工具调用 ID（role=tool 时使用） */
        private String toolCallId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        /** 工具调用 ID */
        private String id;

        /** 工具类型，通常为 "function" */
        private String type;

        /** 工具名称与参数 JSON 字符串 */
        private Function function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Function {
            /** 工具名称 */
            private String name;

            /** 参数 JSON 字符串 */
            private String arguments;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        /** 工具类型，通常为 "function" */
        private String type;

        /** 函数定义 */
        private Function function;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Function {
        /** 函数名称 */
        private String name;

        /** 函数描述 */
        private String description;

        /** JSON Schema 参数定义 */
        private Map<String, Object> parameters;
    }
}
