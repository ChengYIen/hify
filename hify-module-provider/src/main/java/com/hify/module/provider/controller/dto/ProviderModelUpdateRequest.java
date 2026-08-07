package com.hify.module.provider.controller.dto;

import lombok.Data;

/**
 * 更新模型配置请求.
 */
@Data
public class ProviderModelUpdateRequest {

    private String displayName;

    private Integer contextWindow;

    private Integer maxOutput;

    private Integer supportsVision;

    private Integer supportsTools;

    private Integer supportsStreaming;

    private Integer priority;

    private Long fallbackModelId;

    private String status;
}
