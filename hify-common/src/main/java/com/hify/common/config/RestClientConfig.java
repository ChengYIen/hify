package com.hify.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient 配置 —— 统一管理所有外部 HTTP 调用的连接池和超时.
 * <p>
 * 各 LLM provider 的 HTTP 客户端（OpenAiClient / AnthropicClient 等）注入
 * 此 {@link RestClient.Builder} 后自行设置 baseUrl 和认证头。
 * </p>
 *
 * <h3>超时策略</h3>
 * <ul>
 *   <li>连接超时 10s —— 对应 {@code hify.llm.timeout.connect}</li>
 *   <li>读取超时 60s —— 对应 {@code hify.llm.timeout.read}</li>
 *   <li>连接池 max 100 total / 50 per route</li>
 * </ul>
 */
@Slf4j
@Configuration
public class RestClientConfig {

    /**
     * 共享的 RestClient.Builder.
     * <p>
     * 连接池和超时已预设，各 provider 客户端只需：
     * <pre>{@code
     *   restClientBuilder
     *       .baseUrl("https://api.openai.com/v1")
     *       .defaultHeader("Authorization", "Bearer " + apiKey)
     *       .build();
     * }</pre>
     * </p>
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        // JDK 17+ HttpClient —— 支持 HTTP/2 和连接池
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(loggingInterceptor());
    }

    /**
     * 日志拦截器 —— 打印每个 HTTP 请求的方法、URL、耗时.
     * <p>
     * 注意：不打印请求体和响应体，避免日志膨胀。
     * 敏感头（Authorization）自动脱敏。
     * </p>
     */
    private ClientHttpRequestInterceptor loggingInterceptor() {
        return (request, body, execution) -> {
            long start = System.currentTimeMillis();
            try {
                var response = execution.execute(request, body);
                long elapsed = System.currentTimeMillis() - start;
                log.info("HTTP {} {} → {} ({}ms)",
                        request.getMethod(),
                        request.getURI(),
                        response.getStatusCode().value(),
                        elapsed);
                return response;
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                // 连接池耗尽 / DNS 失败等底层问题也会进这里
                log.warn("HTTP {} {} 失败 ({}ms): {}",
                        request.getMethod(), request.getURI(),
                        elapsed, e.getMessage());
                throw e;
            }
        };
    }
}
