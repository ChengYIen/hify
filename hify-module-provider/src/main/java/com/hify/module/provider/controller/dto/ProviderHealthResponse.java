package com.hify.module.provider.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商健康检查记录响应体.
 */
@Data
@Builder
public class ProviderHealthResponse {

    private Long id;
    private Long providerId;
    private String healthStatus;
    private Integer responseTimeMs;
    private String failReason;
    private Integer alertTriggered;
    private LocalDateTime checkedAt;
    private LocalDateTime createdAt;
}
