package com.hify.module.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.provider.controller.dto.ConnectionTestResult;
import com.hify.module.provider.repository.entity.AuthConfig;
import com.hify.module.provider.repository.entity.Provider;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 提供商适配器抽象基类 —— 模板方法模式.
 *
 * <p>封装所有适配器的公共逻辑：URL 解析、HTTP GET 执行、错误格式化。
 * 子类只需实现 5 个抽象方法即可完成适配。</p>
 *
 * <h3>子类需实现的抽象方法</h3>
 * <ul>
 *   <li>{@link #getDefaultBaseUrl()} — 厂商默认 API 地址</li>
 *   <li>{@link #getModelsEndpoint()} — 模型列表端点路径（如 /v1/models）</li>
 *   <li>{@link #getModelsArrayKey()} — 响应体中模型列表的 JSON key（如 data）</li>
 *   <li>{@link #extractModelId(JsonNode)} — 从单个模型 JSON 节点提取标识符</li>
 *   <li>{@link #buildHeaders(AuthConfig)} — 根据鉴权配置构建请求头</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractProviderAdapter implements ProviderAdapter {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    protected final LlmHttpClient llmHttpClient;
    protected final ObjectMapper objectMapper;

    protected AbstractProviderAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        this.llmHttpClient = llmHttpClient;
        this.objectMapper = objectMapper;
    }

    // ================================================================
    // 模板方法：子类覆写
    // ================================================================

    /** 厂商默认 API 基础地址，返回 {@code null} 表示必须由用户配置 */
    protected abstract String getDefaultBaseUrl();

    /** 模型列表端点路径，如 {@code /v1/models} */
    protected abstract String getModelsEndpoint();

    /** 响应 JSON 中模型数组的 key，如 {@code data}（OpenAI）或 {@code models}（Ollama） */
    protected abstract String getModelsArrayKey();

    /** 从模型 JSON 节点提取标识符，如 OpenAI 用 {@code id}，Ollama 用 {@code name} */
    protected abstract String extractModelId(JsonNode item);

    /** 根据鉴权配置构建 HTTP 请求头 */
    protected abstract Map<String, String> buildHeaders(AuthConfig auth);

    // ================================================================
    // ProviderAdapter 实现
    // ================================================================

    @Override
    public ConnectionTestResult testConnection(Provider provider) {
        String url = resolveUrl(provider.getBaseUrl());
        if (url == null) {
            return ConnectionTestResult.builder()
                    .success(false)
                    .latencyMs(0)
                    .modelCount(0)
                    .errorMessage(provider.getProviderCode() + " 必须配置 baseUrl")
                    .build();
        }
        log.info("连通性测试开始: providerId={}, code={}, baseUrl={}",
                provider.getId(), provider.getProviderCode(), url);
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());
        return executeGet(url + getModelsEndpoint(), headers, getModelsArrayKey());
    }

    @Override
    public List<String> listModels(Provider provider) {
        String url = resolveUrl(provider.getBaseUrl());
        if (url == null) {
            log.warn("listModels 失败: baseUrl 未配置, providerId={}", provider.getId());
            return List.of();
        }
        String fullUrl = url + getModelsEndpoint();
        Map<String, String> headers = buildHeaders(provider.getAuthConfig());
        return executeGetForModels(fullUrl, headers, getModelsArrayKey());
    }

    // ================================================================
    // 公共工具方法
    // ================================================================

    /**
     * 解析实际使用的 base URL.
     * <p>已配置 → 使用配置值；未配置 → 使用厂商默认值。去除末尾斜杠。</p>
     *
     * @return 解析后的 URL，如果用户未配置且厂商无默认值则返回 {@code null}
     */
    protected String resolveUrl(String configuredUrl) {
        String base = (configuredUrl != null && !configuredUrl.isBlank())
                ? configuredUrl.strip()
                : getDefaultBaseUrl();

        if (base == null || base.isBlank()) {
            return null;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * 执行 GET 请求，解析模型数量，统一转换为 {@link ConnectionTestResult}.
     */
    protected ConnectionTestResult executeGet(String url, Map<String, String> headers, String arrayKey) {
        long start = System.currentTimeMillis();
        try {
            String body = llmHttpClient.get(url, headers, TEST_TIMEOUT);
            long latency = System.currentTimeMillis() - start;
            int modelCount = parseModelCount(body, arrayKey);
            log.info("连通性测试成功: url={}, latency={}ms, modelCount={}", url, latency, modelCount);
            return ConnectionTestResult.builder()
                    .success(true)
                    .latencyMs(latency)
                    .modelCount(modelCount)
                    .build();
        } catch (LlmApiException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("连通性测试失败: url={}, type={}, status={}, msg={}",
                    url, e.getType(), e.getStatusCode(), e.getMessage());
            return ConnectionTestResult.builder()
                    .success(false)
                    .latencyMs(latency)
                    .modelCount(0)
                    .errorMessage(formatError(e))
                    .build();
        }
    }

    /**
     * 执行 GET 请求，返回模型标识符列表.
     */
    protected List<String> executeGetForModels(String url, Map<String, String> headers, String arrayKey) {
        try {
            String body = llmHttpClient.get(url, headers, TEST_TIMEOUT);
            return parseModelList(body, arrayKey);
        } catch (LlmApiException e) {
            log.warn("获取模型列表失败: url={}, type={}, msg={}", url, e.getType(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 JSON 响应体中提取模型数组长度.
     */
    protected int parseModelCount(String body, String arrayKey) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode array = root.get(arrayKey);
            return array != null && array.isArray() ? array.size() : 0;
        } catch (Exception e) {
            log.warn("解析模型列表 JSON 失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 从 JSON 响应体中提取模型标识符列表.
     */
    protected List<String> parseModelList(String body, String arrayKey) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode array = root.get(arrayKey);
            if (array == null || !array.isArray()) {
                return List.of();
            }
            List<String> models = new ArrayList<>(array.size());
            for (JsonNode item : array) {
                String id = extractModelId(item);
                if (id != null && !id.isBlank()) {
                    models.add(id);
                }
            }
            return models;
        } catch (Exception e) {
            log.warn("解析模型列表 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将 {@link LlmApiException} 转译为面向用户的错误信息.
     */
    protected String formatError(LlmApiException e) {
        return switch (e.getType()) {
            case TIMEOUT       -> "连接超时: 请检查网络或 baseUrl 是否正确";
            case AUTH_FAILED   -> "认证失败 (401): API Key 无效或已过期";
            case NETWORK_ERROR -> "网络不可达: 请检查 baseUrl 和网络连接";
            case SERVER_ERROR  -> "服务器错误 (" + e.getStatusCode() + "): 提供商服务异常，请稍后重试";
            case RATE_LIMITED  -> "请求过于频繁 (429): 请稍后重试";
        };
    }
}
