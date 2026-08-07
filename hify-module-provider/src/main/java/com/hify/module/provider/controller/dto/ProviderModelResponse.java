package com.hify.module.provider.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型配置响应体.
 */
@Data
@Builder
public class ProviderModelResponse {

    private Long id;
    private Long providerId;
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
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
