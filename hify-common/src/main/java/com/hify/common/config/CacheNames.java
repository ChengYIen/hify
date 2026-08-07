package com.hify.common.config;

/**
 * 缓存名常量 —— 统一管理，避免手写字符串.
 * <p>
 * 使用方式：{@code @Cacheable(cacheNames = CacheNames.PROVIDER)}。
 * 缓存名无需包含 {@code hify:} 前缀，由 {@code CacheConfig.prefixCacheNameWith("hify:")} 统一追加。
 * </p>
 *
 * <h3>TTL 策略</h3>
 * <table>
 *   <tr><th>缓存</th><th>TTL</th><th>理由</th></tr>
 *   <tr><td>PROVIDER</td><td>30 min</td><td>提供商配置变更频率低，变更后主动失效</td></tr>
 *   <tr><td>AGENT</td><td>30 min</td><td>Agent 配置同上</td></tr>
 *   <tr><td>SESSION</td><td>2 hours</td><td>对话中断后可恢复，需要较长保留时间</td></tr>
 * </table>
 */
public final class CacheNames {

    private CacheNames() {
    }

    /** 模型提供商配置 */
    public static final String PROVIDER = "provider-cache";

    /** Agent 配置 */
    public static final String AGENT = "agent-cache";

    /** 对话会话状态 */
    public static final String SESSION = "session-cache";
}
