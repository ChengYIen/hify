package com.hify.module.provider.repository.entity;

/**
 * 模型发现方式枚举.
 */
public enum DiscoveryType {

    /** 调用 provider API 自动获取模型列表（如 Ollama GET /api/tags、OpenAI GET /v1/models） */
    AUTO,

    /** 手动在管理后台添加/维护模型列表（如 Claude、Gemini 等模型列表小而稳定的提供商） */
    MANUAL;
}
