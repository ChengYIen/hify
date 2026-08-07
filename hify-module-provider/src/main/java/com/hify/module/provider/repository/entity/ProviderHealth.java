package com.hify.module.provider.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提供商健康检查记录实体.
 *
 * <p>对应表 {@code hify_provider_health}。每次健康检查产生一条记录，
 * 用于追踪 provider 的可用性历史。不继承 {@link com.hify.common.entity.BaseEntity}，
 * 有自己的主键和时间字段结构。</p>
 *
 * <h3>与 Provider.healthStatus 的关系</h3>
 * <ul>
 *   <li>{@link Provider#getHealthStatus()} 保存 <b>当前</b> 健康状态（最新一条检查结果）</li>
 *   <li>本表保存健康检查 <b>历史记录</b>，用于趋势分析和告警</li>
 * </ul>
 */
@Data
@TableName("hify_provider_health")
public class ProviderHealth {

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属提供商实例 ID（关联 hify_provider.id） */
    private Long providerId;

    /** 健康状态：HEALTHY / UNHEALTHY / DEGRADED / UNKNOWN */
    private String healthStatus;

    /** 响应时间（毫秒），-1 表示不可达 */
    private Integer responseTimeMs;

    /** 失败原因（HEALTHY 时为 null） */
    private String failReason;

    /** 是否触发告警 0=否 1=是 */
    private Integer alertTriggered;

    /** 健康检查执行时间 */
    private LocalDateTime checkedAt;

    /** 记录创建时间 */
    private LocalDateTime createdAt;
}
