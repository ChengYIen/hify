package com.hify.module.provider.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.provider.controller.dto.ConnectionTestResult;
import com.hify.module.provider.repository.ProviderHealthMapper;
import com.hify.module.provider.repository.ProviderMapper;
import com.hify.module.provider.repository.entity.Provider;
import com.hify.module.provider.repository.entity.ProviderHealth;
import com.hify.module.provider.service.ProviderConnectivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供商健康检查定时任务.
 *
 * <p>每分钟遍历所有 enabled 的 provider，通过连通性测试检查实际可用性。
 * 检查任务提交到 {@code asyncExecutor} 线程池异步执行，不阻塞调度线程。</p>
 *
 * <h3>状态转换规则</h3>
 * <ul>
 *   <li>成功 → healthStatus=HEALTHY, failCount 归零, 记录 lastSuccessAt</li>
 *   <li>失败 → failCount+1, 连续失败 ≥3 次 → healthStatus=UNHEALTHY</li>
 *   <li>每次检查插入一条 {@link ProviderHealth} 历史记录</li>
 * </ul>
 *
 * <h3>线程模型</h3>
 * <ul>
 *   <li>调度线程（scheduling-1）：查询 enabled provider 列表，提交任务后立即返回</li>
 *   <li>asyncExecutor 线程池：执行实际的连通性测试 + 数据库更新</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderHealthCheckScheduler {

    private final ProviderMapper providerMapper;
    private final ProviderHealthMapper providerHealthMapper;
    private final ProviderConnectivityService connectivityService;

    @Qualifier("asyncExecutor")
    private final ThreadPoolTaskExecutor asyncExecutor;

    /** 连续失败次数阈值，达到后标记为 UNHEALTHY */
    private static final int FAILURE_THRESHOLD = 3;

    // ================================================================
    // 定时入口
    // ================================================================

    /**
     * 每分钟执行一次，遍历所有 enabled 的 provider 提交异步健康检查.
     * <p>调度线程只做查询 + 提交，不执行阻塞 IO，几十毫秒内返回。</p>
     */
    @Scheduled(fixedRate = 60_000)
    public void checkAllProviders() {
        List<Provider> providers = providerMapper.selectList(
                new LambdaQueryWrapper<Provider>()
                        .eq(Provider::getStatus, "ENABLED"));

        if (providers.isEmpty()) {
            return;
        }

        log.info("健康检查调度: 提交 {} 个 provider 检查任务", providers.size());
        for (Provider provider : providers) {
            asyncExecutor.submit(() -> checkOne(provider));
        }
    }

    // ================================================================
    // 单 provider 检查
    // ================================================================

    /**
     * 对单个 provider 执行连通性测试并更新状态.
     * <p>运行在 asyncExecutor 线程池中。</p>
     */
    private void checkOne(Provider provider) {
        Long providerId = provider.getId();
        String name = provider.getName();
        LocalDateTime now = LocalDateTime.now();

        try {
            ConnectionTestResult result = connectivityService.testConnection(providerId);

            if (result.isSuccess()) {
                handleSuccess(provider, result, now);
            } else {
                handleFailure(provider, result.getErrorMessage(), result.getLatencyMs(), now);
            }
        } catch (Exception e) {
            log.error("健康检查异常: providerId={}, name={}", providerId, name, e);
            handleFailure(provider, "健康检查执行异常: " + e.getMessage(), -1, now);
        }
    }

    // ================================================================
    // 成功 / 失败处理
    // ================================================================

    /**
     * 处理连通性测试成功：状态置 HEALTHY，failCount 归零，记录 lastSuccessAt.
     */
    private void handleSuccess(Provider provider, ConnectionTestResult result, LocalDateTime checkedAt) {
        provider.setHealthStatus("HEALTHY");
        provider.setFailCount(0);
        provider.setLastSuccessAt(checkedAt);
        provider.setLastHealthCheckAt(checkedAt);
        provider.setHealthFailReason(null);
        providerMapper.updateById(provider);

        ProviderHealth record = new ProviderHealth();
        record.setProviderId(provider.getId());
        record.setHealthStatus("HEALTHY");
        record.setResponseTimeMs((int) result.getLatencyMs());
        record.setAlertTriggered(0);
        record.setCheckedAt(checkedAt);
        providerHealthMapper.insert(record);

        log.info("健康检查成功: provider={}, latency={}ms, models={}",
                provider.getName(), result.getLatencyMs(), result.getModelCount());
    }

    /**
     * 处理连通性测试失败：failCount+1，连续 ≥3 次标记 UNHEALTHY.
     */
    private void handleFailure(Provider provider, String errorMessage, long latencyMs, LocalDateTime checkedAt) {
        int currentFailCount = provider.getFailCount() != null ? provider.getFailCount() : 0;
        int newFailCount = currentFailCount + 1;
        boolean thresholdReached = newFailCount >= FAILURE_THRESHOLD;

        String newHealthStatus = thresholdReached ? "UNHEALTHY"
                : (provider.getHealthStatus() != null ? provider.getHealthStatus() : "UNKNOWN");

        provider.setFailCount(newFailCount);
        provider.setHealthStatus(newHealthStatus);
        provider.setLastHealthCheckAt(checkedAt);
        provider.setHealthFailReason(errorMessage);
        providerMapper.updateById(provider);

        ProviderHealth record = new ProviderHealth();
        record.setProviderId(provider.getId());
        record.setHealthStatus(newHealthStatus);
        record.setResponseTimeMs((int) latencyMs);
        record.setFailReason(errorMessage);
        record.setAlertTriggered(thresholdReached ? 1 : 0);
        record.setCheckedAt(checkedAt);
        providerHealthMapper.insert(record);

        if (thresholdReached) {
            log.warn("健康检查失败达到阈值: provider={}, failCount={}/{}, status→UNHEALTHY, reason={}",
                    provider.getName(), newFailCount, FAILURE_THRESHOLD, errorMessage);
        } else {
            log.info("健康检查失败: provider={}, failCount={}/{}, reason={}",
                    provider.getName(), newFailCount, FAILURE_THRESHOLD, errorMessage);
        }
    }
}
