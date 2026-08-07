package com.hify.module.provider.repository.entity;

/**
 * 模型类型枚举.
 */
public enum ModelType {

    /** 大语言模型（对话/补全） */
    LLM,

    /** 文本嵌入模型（用于 RAG 向量化） */
    EMBEDDING,

    /** 图片生成模型 */
    IMAGE,

    /** 语音合成模型 */
    TTS,

    /** 重排序模型（RAG 检索后排序） */
    RERANK;
}
