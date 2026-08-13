package com.hify.module.provider.adapter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 请求 DTO —— ProviderAdapter 层的统一入参.
 *
 * <p>与 {@link ChatRequest} 同层：包含传输层配置（baseUrl / apiKey）和业务参数（model / inputs）。
 * 各适配器实现负责转换为对应厂商的 HTTP 请求格式。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    // ---- 传输层 ----

    /** 提供商 API 基础地址，{@code null} 时使用适配器默认值 */
    private String baseUrl;

    /** API 密钥（明文，调用方从数据库解密后传入） */
    private String apiKey;

    // ---- 请求参数 ----

    /** 模型名称（如 text-embedding-3-small） */
    private String model;

    /** 待向量化文本列表（最多建议 100~2000 条/请求） */
    private List<String> inputs;

    /**
     * 输出维度（可空）。
     * <p>仅 3 系列模型支持（Matryoshka 降维），省略则用模型默认维度
     * （如 text-embedding-3-small 默认 1536，ada-002 不支持该参数）。</p>
     */
    private Integer dimensions;
}
