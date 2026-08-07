package com.hify.module.provider.controller.dto;

import com.hify.module.provider.repository.entity.AuthConfig;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建提供商请求.
 */
@Data
public class ProviderCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    /** 鉴权配置（JSON 对象，前端直接传嵌套 JSON） */
    private AuthConfig authConfig;

    private String baseUrl;

    private String discoveryType;

    private Integer priority;
}
