package com.hify.shared.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LLM 统一请求 DTO.
 * <p>
 * 各 provider 实现负责将此 DTO 转换为对应厂商的 API 请求格式。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRequestDTO {

    /** 模型配置 ID（模型路由由 provider 模块内部完成，调用方不感知厂商/密钥） */
    private Long modelId;

    /** 模型名称（如 gpt-4o / claude-sonnet-4-20250514） */
    private String model;

    /** 对话消息列表 */
    private List<Message> messages;

    /** 温度 0.0–2.0，LLM 调用层统一管理，业务层不从请求传递 */
    private Double temperature;

    /** 最大 Token 数 */
    private Integer maxTokens;

    /** 流式输出（SSE） */
    private boolean stream;

    /** 厂商特有参数透传（如 OpenAI 的 top_p / frequency_penalty 等） */
    private Map<String, Object> extra;

    // ----------------------------------------------------
    // 内嵌类
    // ----------------------------------------------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /** system / user / assistant / tool */
        private String role;
        /** 消息文本内容 */
        private String content;
        /** 工具调用 ID（role=tool 时使用） */
        private String toolCallId;
    }
}
