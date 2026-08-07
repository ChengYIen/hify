package com.hify.shared.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 提供商实例 DTO（跨模块共享）.
 * <p>
 * 用于其他模块（conversation / agent）查询提供商实例信息。
 * 不暴露 apiKey 等敏感字段，authConfig 字段不在 shared 层暴露。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDTO {

    /** 实例 ID */
    private Long id;

    /** 实例名称 */
    private String name;

    /** 适配器编码 */
    private String providerCode;

    /** API 基础地址 */
    private String baseUrl;

    /** 健康状态 */
    private String healthStatus;

    /** 优先级 */
    private Integer priority;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
