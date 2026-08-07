package com.hify.module.provider.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.provider.controller.dto.ProviderModelCreateRequest;
import com.hify.module.provider.controller.dto.ProviderModelResponse;
import com.hify.module.provider.controller.dto.ProviderModelUpdateRequest;

import java.util.List;

/**
 * 模型配置业务接口.
 */
public interface ProviderModelService {

    IPage<ProviderModelResponse> page(int page, int pageSize);

    ProviderModelResponse getById(Long id);

    List<ProviderModelResponse> listByProviderId(Long providerId);

    ProviderModelResponse create(ProviderModelCreateRequest request);

    ProviderModelResponse update(Long id, ProviderModelUpdateRequest request);

    void delete(Long id);

    /**
     * 列出所有已启用的模型配置（跨 provider，用于 Agent 下拉选择）.
     *
     * @return 所有已启用模型列表
     */
    List<ProviderModelResponse> listAllEnabled();
}
