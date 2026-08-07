package com.hify.module.provider.controller.dto;

import com.hify.module.provider.repository.entity.AuthConfig;
import lombok.Data;

/**
 * 更新提供商请求.
 */
@Data
public class ProviderUpdateRequest {

    private String name;

    private String description;

    /** 鉴权配置（JSON 对象，前端直接传嵌套 JSON） */
    private AuthConfig authConfig;

    private String baseUrl;

    private String status;

    private Integer priority;
}
