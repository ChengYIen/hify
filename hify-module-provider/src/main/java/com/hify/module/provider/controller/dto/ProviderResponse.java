package com.hify.module.provider.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供商响应体（不含 apiKey 明文）.
 *
 * <p>列表查询时 {@link #modelConfigs} 和 {@link #latestHealth} 为 null，
 * 仅详情接口填充关联数据。</p>
 */
@Data
@Builder
public class ProviderResponse {

    private Long id;
    private String name;
    private String description;
    private String providerCode;
    private String baseUrl;
    private String status;
    private String healthStatus;
    private String discoveryType;
    private Integer priority;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联的模型配置列表（仅详情接口填充） */
    private List<ProviderModelResponse> modelConfigs;

    /** 最近一次健康检查记录（仅详情接口填充） */
    private ProviderHealthResponse latestHealth;

    /** 已启用模型数量（列表接口填充） */
    private Integer modelCount;

    /** 最近一次健康检查响应时间 ms（列表接口填充，-1 表示不可达） */
    private Integer lastHealthResponseTimeMs;
}
