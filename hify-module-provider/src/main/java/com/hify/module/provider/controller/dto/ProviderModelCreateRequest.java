package com.hify.module.provider.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建模型配置请求.
 */
@Data
public class ProviderModelCreateRequest {

    /** 由 Controller 从路径变量注入，无需在请求体中传递 */
    private Long providerId;

    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    private String displayName;

    private String modelType;

    private Integer contextWindow;

    private Integer maxOutput;

    private Integer supportsVision;

    private Integer supportsTools;

    private Integer supportsStreaming;

    private Integer priority;

    private Long fallbackModelId;
}
