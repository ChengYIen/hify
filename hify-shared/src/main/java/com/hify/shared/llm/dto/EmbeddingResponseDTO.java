package com.hify.shared.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 统一响应 DTO.
 * <p>
 * provider 模块负责将厂商响应转换为此 DTO，knowledge 模块不感知底层厂商。
 * {@link #embeddings} 与入参 {@code List<String> texts} 顺序一一对应。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponseDTO {

    /** 实际使用的模型名称 */
    private String model;

    /** 向量列表，下标与入参文本顺序一致 */
    private List<List<Float>> embeddings;

    /** Token 用量（embedding 只按输入计费，无 completionTokens） */
    private LlmResponseDTO.TokenUsage usage;

    /** 响应耗时（毫秒） */
    private Long latencyMs;
}
