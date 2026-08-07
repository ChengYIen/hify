package com.hify.module.provider.repository.entity;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权配置 POJO，配合 MyBatis-Plus JacksonTypeHandler 序列化/反序列化 JSON.
 *
 * <p>不同 provider_code 对应不同字段组合：</p>
 * <pre>
 * openai / openai_compatible → {"apiKey": "sk-xxx"}
 * anthropic                  → {"apiKey": "sk-ant-xxx", "anthropicVersion": "2023-06-01"}
 * gemini                     → {"apiKey": "xxx"}
 * azure                      → {"apiKey": "xxx"}
 * ollama / vllm              → {}
 * </pre>
 * 未来新增供应商零改表，直接扩展 extra 字段。
 */
@Data
public class AuthConfig {

    /** API 密钥（加密存储） */
    private String apiKey;

    /** API 密钥 ID（如 Azure 的 keyId / 部分网关要求） */
    private String apiKeyId;

    /** Anthropic 专用：API 版本号 */
    private String anthropicVersion;

    /**
     * 扩展字段，存放上述标准字段以外的鉴权参数.
     * <p>Jackson 自动将 JSON 中未映射的 key 放入此 Map。</p>
     */
    private Map<String, Object> extra = new LinkedHashMap<>();
}
