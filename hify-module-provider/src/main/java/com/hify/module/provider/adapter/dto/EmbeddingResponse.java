package com.hify.module.provider.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 响应 DTO —— ProviderAdapter 层统一出参.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {

    /** 实际使用的模型名称 */
    private String model;

    /** 向量列表，下标与入参 {@code inputs} 顺序一致 */
    private List<List<Float>> embeddings;

    /** 输入 Token 数（embedding 只按输入计费） */
    private Integer promptTokens;

    /** 响应耗时（毫秒），由调用方回填 */
    private Long latencyMs;
}
