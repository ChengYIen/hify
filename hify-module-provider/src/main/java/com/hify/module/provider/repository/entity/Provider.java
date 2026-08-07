package com.hify.module.provider.repository.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 模型提供商实体.
 *
 * <p>对应表 {@code hify_provider}。每个实例代表一个可用的 LLM 提供商连接配置——
 * 同一个 provider_code（如 openai）可以有多个实例，各自使用不同的 API Key 和 Base URL。</p>
 *
 * <h3>鉴权模式</h3>
 * {@link #authConfig} 使用 {@link JacksonTypeHandler} 序列化，
 * 不同 provider_code 对应不同 JSON 结构，未来新增供应商零改表。
 *
 * <h3>健康状态</h3>
 * {@link #status} 是用户手动开关，{@link #healthStatus} 是系统定时检查的实际连通性，
 * 二者独立管理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "hify_provider", autoResultMap = true)
public class Provider extends BaseEntity {

    // -------------------------------------------------------
    // 基本信息
    // -------------------------------------------------------

    /** 实例名称（如 "OpenAI 个人号" / "公司 Azure 东亚区"） */
    private String name;

    /** 备注说明 */
    private String description;

    /** 适配器编码（openai / claude / gemini / ollama），决定使用哪个 Java 客户端 */
    private String providerCode;

    // -------------------------------------------------------
    // 鉴权
    // -------------------------------------------------------

    /**
     * 鉴权配置（JSON，按 provider_code 存不同结构，apiKey 加密存储）.
     * <p>使用 JacksonTypeHandler 自动序列化/反序列化 {@link AuthConfig}。</p>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AuthConfig authConfig;

    // -------------------------------------------------------
    // 端点
    // -------------------------------------------------------

    /** API 基础地址，为 null 则使用适配器内置默认值 */
    private String baseUrl;

    // -------------------------------------------------------
    // 状态
    // -------------------------------------------------------

    /** 用户开关：ENABLED / DISABLED */
    private String status;

    /** 健康状态（系统维护）：HEALTHY / UNHEALTHY / DEGRADED / UNKNOWN */
    private String healthStatus;

    /** 最近一次健康检查时间 */
    private LocalDateTime lastHealthCheckAt;

    /** 最近一次健康检查失败原因 */
    private String healthFailReason;

    /** 连续失败次数（成功时归零，连续失败 ≥3 次标记 UNHEALTHY） */
    private Integer failCount;

    /** 最近一次健康检查成功时间 */
    private LocalDateTime lastSuccessAt;

    // -------------------------------------------------------
    // 模型管理
    // -------------------------------------------------------

    /** 模型发现方式：AUTO（调 API 自动同步）/ MANUAL（手动维护） */
    private String discoveryType;

    /** 最近一次自动同步模型列表的时间 */
    private LocalDateTime lastSyncedAt;

    // -------------------------------------------------------
    // 排序
    // -------------------------------------------------------

    /** 优先级，数值越大越优先（多实例时用于选择默认 provider） */
    private Integer priority;
}
