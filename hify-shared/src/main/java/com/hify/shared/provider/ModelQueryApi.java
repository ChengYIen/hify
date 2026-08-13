package com.hify.shared.provider;

/**
 * 模型查询接口（跨模块共享）.
 * <p>
 * Agent 模块通过此接口校验 modelId 是否存在且可用，
 * 避免跨模块直接 import provider 模块的 Mapper。
 * </p>
 */
public interface ModelQueryApi {

    /**
     * 校验模型是否存在且处于启用状态.
     *
     * @param modelId 模型 ID
     * @return true=模型存在且可用
     */
    boolean isModelAvailable(Long modelId);

    /**
     * 获取第一个可用的模型 ID（模型本身启用且所属提供商启用）.
     *
     * <p>供新建会话等需要"默认模型"的场景使用——客户端不指定模型时，
     * 由调用方用本方法选一个可用模型兜底。</p>
     *
     * @return 第一个可用的模型 ID；无任何可用模型返回 null
     */
    Long getFirstEnabledModelId();

    /**
     * 获取第一个启用的 Embedding 模型 ID.
     *
     * <p>供 knowledge 模块创建知识库时兜底——客户端未指定 Embedding 模型时，
     * 自动选一个 {@code modelType = EMBEDDING} 且提供商启用的模型。</p>
     *
     * @return 第一个可用的 Embedding 模型 ID；没有则返回 null
     */
    Long getFirstEnabledEmbeddingModelId();
}
