package com.hify.module.provider.repository.entity;

/**
 * 提供商健康状态枚举.
 * <p>
 * 与用户开关 {@code status(ENABLED/DISABLED)} 独立——用户启用的 provider 可能因网络/配额
 * 问题处于 UNHEALTHY，此时应触发降级或告警。
 * </p>
 *
 * <pre>
 * 状态机：
 *   UNKNOWN ──(健康检查通过)──▶ HEALTHY
 *   HEALTHY ──(健康检查失败)──▶ UNHEALTHY
 *   UNHEALTHY ──(健康检查恢复)──▶ HEALTHY
 *   UNKNOWN / HEALTHY ──(连续部分失败)──▶ DEGRADED（二期）
 * </pre>
 */
public enum HealthStatus {

    /** 健康检查通过，可以正常调用 */
    HEALTHY,

    /** 健康检查失败，不可用（触发告警 + 降级） */
    UNHEALTHY,

    /** 间歇性失败（如频繁 429），可用但建议降级（二期实现） */
    DEGRADED,

    /** 从未执行过健康检查，或 provider 刚创建 */
    UNKNOWN;

    /**
     * 是否可用于接受新请求.
     * HEALTHY 和 DEGRADED 可以（DEGRADED 是软降级），UNHEALTHY 和 UNKNOWN 不可以。
     */
    public boolean canAcceptRequests() {
        return this == HEALTHY || this == DEGRADED;
    }
}
