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
}
