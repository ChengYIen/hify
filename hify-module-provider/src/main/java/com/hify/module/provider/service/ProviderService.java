package com.hify.module.provider.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.provider.controller.dto.ProviderCreateRequest;
import com.hify.module.provider.controller.dto.ProviderResponse;
import com.hify.module.provider.controller.dto.ProviderUpdateRequest;

/**
 * 模型提供商业务接口.
 */
public interface ProviderService {

    /**
     * 分页列表，支持按 providerCode 和 status 筛选.
     *
     * @param page         页码（从 1 开始）
     * @param pageSize     每页条数
     * @param providerCode 提供商编码（可选，如 openai / claude）
     * @param status       状态筛选（可选，如 ENABLED / DISABLED）
     */
    IPage<ProviderResponse> list(int page, int pageSize, String providerCode, String status);

    /**
     * 详情查询，包含关联的 modelConfig 列表和最近一次健康检查记录.
     */
    ProviderResponse getById(Long id);

    /**
     * 创建提供商，校验名称不重复.
     */
    ProviderResponse create(ProviderCreateRequest request);

    /**
     * 更新提供商.
     */
    ProviderResponse update(Long id, ProviderUpdateRequest request);

    /**
     * 删除提供商（逻辑删除）.
     */
    void delete(Long id);
}
