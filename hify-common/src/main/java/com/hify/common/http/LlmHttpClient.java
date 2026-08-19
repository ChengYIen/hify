package com.hify.common.http;

import io.micrometer.context.ContextSnapshot;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * LLM HTTP 调用客户端.
 * <p>
 * 内部持有两个 HTTP 客户端，职责分离：
 * <ul>
 *   <li>{@link RestTemplate} — 同步 POST（连接 5s，读取 60s），用于普通 LLM 调用</li>
 *   <li>{@link OkHttpClient} — 异步 SSE 流式（连接 5s，读取 120s），用于流式对话</li>
 * </ul>
 * 所有请求统一记日志（URL、耗时、状态码），底层异常统一转为 {@link LlmApiException}。
 * </p>
 */
@Slf4j
@Component
public class LlmHttpClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // ----------------------------------------------------------------
    // 超时常量
    // ----------------------------------------------------------------
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SYNC_READ_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_READ_TIMEOUT = Duration.ofSeconds(120);

    // ----------------------------------------------------------------
    // 内部客户端
    // ----------------------------------------------------------------
    private final RestTemplate restTemplate;
    private final OkHttpClient okHttpClient;

    public LlmHttpClient() {
        // --- RestTemplate: 同步调用 ---
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(SYNC_READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);

        // --- OkHttpClient: SSE 流式调用 ---
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(STREAM_READ_TIMEOUT)
                .build();

        log.info("LlmHttpClient 初始化完成: RestTemplate(read=60s) + OkHttp(read=120s)");
    }

    // ================================================================
    // 同步 POST — 普通 LLM 调用
    // ================================================================

    /**
     * 同步 POST 请求，返回响应体字符串.
     *
     * @param url     请求 URL
     * @param headers 请求头
     * @param body    请求体（JSON 字符串）
     * @return 响应体字符串
     * @throws LlmApiException 按 HTTP 状态码或异常类型分类抛出
     */
    public String post(String url, Map<String, String> headers, String body) {
        long start = System.currentTimeMillis();

        // 组装 Spring HTTP 头
        org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
        headers.forEach(httpHeaders::add);

        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>(body, httpHeaders);

        try {
            var response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            int status = response.getStatusCode().value();
            long elapsed = System.currentTimeMillis() - start;

            log.info("LLM POST {} → {} ({}ms)", url, status, elapsed);

            // 按状态码分类抛异常
            throwIfError(status, url);
            return response.getBody();

        } catch (LlmApiException e) {
            // 已经是分类过的异常，直接抛
            throw e;
        } catch (ResourceAccessException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("LLM POST {} 网络异常 ({}ms): {}", url, elapsed, e.getMessage());
            throw classifyNetworkError(e.getCause(), url);
        } catch (RestClientException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("LLM POST {} 客户端异常 ({}ms): {}", url, elapsed, e.getMessage());
            throw new LlmApiException(LlmApiException.Type.NETWORK_ERROR, url, e);
        }
    }

    // ================================================================
    // 同步 GET — 连通性测试等场景
    // ================================================================

    /**
     * 同步 GET 请求，返回响应体字符串.
     * <p>
     * 用于连通性测试等场景（如调 /v1/models 验证 API Key 可用性）。
     * 内部使用 OkHttp，通过 {@code timeout} 控制整体超时。
     * </p>
     *
     * @param url     请求 URL
     * @param headers 请求头（可为 {@code null} 或空 Map）
     * @param timeout 读取超时（连接超时复用全局 5s 配置）
     * @return 响应体字符串（非 2xx 时抛 {@link LlmApiException}）
     * @throws LlmApiException 按 HTTP 状态码或异常类型分类抛出
     */
    public String get(String url, Map<String, String> headers, Duration timeout) {
        long start = System.currentTimeMillis();

        // 在全局 OkHttpClient 基础上覆盖 read timeout
        OkHttpClient client = this.okHttpClient.newBuilder()
                .readTimeout(timeout)
                .build();

        Headers okHeaders = Headers.of(headers != null ? headers : Collections.emptyMap());
        Request request = new Request.Builder()
                .url(url)
                .headers(okHeaders)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            int status = response.code();
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM GET {} → {} ({}ms)", url, status, elapsed);

            throwIfError(status, url);

            ResponseBody body = response.body();
            return body != null ? body.string() : "";

        } catch (LlmApiException e) {
            throw e;
        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("LLM GET {} 网络异常 ({}ms): {}", url, elapsed, e.getMessage());
            throw classifyNetworkError(e, url);
        }
    }

    // ================================================================
    // 异步 SSE POST — 流式 LLM 调用
    // ================================================================

    /**
     * 异步 SSE 流式 POST，逐行回调 {@link StreamCallback#onLine(String)}.
     * <p>
     * 方法立即返回，不阻塞调用线程。SSE 数据在 OkHttp 内部线程池中读取后回调。
     * 自动跳过空行和事件边界标记，只提取 {@code data:} 前缀后的内容。
     * 返回 {@link Call} 供调用方在客户端断开等场景主动 {@code cancel()} 中断请求。
     * </p>
     *
     * @param url      请求 URL
     * @param headers  请求头
     * @param body     请求体（JSON 字符串）
     * @param callback 逐行回调
     * @return 已 enqueue 的 {@link Call}，可随时取消
     */
    public Call stream(String url, Map<String, String> headers, String body, StreamCallback callback) {
        long start = System.currentTimeMillis();
        ContextSnapshot contextSnapshot = ContextSnapshot.captureAll();

        Headers okHeaders = Headers.of(headers);
        Request request = new Request.Builder()
                .url(url)
                .headers(okHeaders)
                .post(RequestBody.create(body, JSON))
                .build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                try (ContextSnapshot.Scope ignored = contextSnapshot.setThreadLocals()) {
                    long elapsed = System.currentTimeMillis() - start;
                    log.warn("LLM SSE {} 连接失败 ({}ms): {}", url, elapsed, e.getMessage());
                    callback.onError(classifyNetworkError(e, url));
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ContextSnapshot.Scope ignored = contextSnapshot.setThreadLocals()) {
                    int status = response.code();
                    log.info("LLM SSE {} → {} (stream begin)", url, status);

                    // 状态码校验
                    try {
                        throwIfError(status, url);
                    } catch (LlmApiException e) {
                        response.close();
                        callback.onError(e);
                        return;
                    }

                    // 读取流式响应体
                    ResponseBody responseBody = response.body();
                    if (responseBody == null) {
                        callback.onComplete();
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(responseBody.byteStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // SSE 规范：data: 前缀行才传给回调
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                callback.onLine(data);
                            }
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        log.info("LLM SSE {} 完成 ({}ms)", url, elapsed);
                        callback.onComplete();

                    } catch (IOException e) {
                        long elapsed = System.currentTimeMillis() - start;
                        log.warn("LLM SSE {} 读取中断 ({}ms): {}", url, elapsed, e.getMessage());
                        callback.onError(classifyNetworkError(e, url));
                    }
                }
            }
        });

        return call;
    }

    // ================================================================
    // 私有方法
    // ================================================================

    /**
     * 按 HTTP 状态码分类抛出对应 {@link LlmApiException}.
     */
    private void throwIfError(int status, String url) {
        if (status == 401) {
            throw new LlmApiException(LlmApiException.Type.AUTH_FAILED, status, url);
        }
        if (status == 429) {
            throw new LlmApiException(LlmApiException.Type.RATE_LIMITED, status, url);
        }
        if (status >= 500) {
            throw new LlmApiException(LlmApiException.Type.SERVER_ERROR, status, url);
        }
    }

    /**
     * 将 IO 异常分类为 {@link LlmApiException}.
     * <p>
     * 区分连接超时、读取超时、DNS/连接失败等不同网络故障。
     * </p>
     */
    private LlmApiException classifyNetworkError(Throwable cause, String url) {
        if (cause == null) {
            return new LlmApiException(LlmApiException.Type.NETWORK_ERROR, 0, url);
        }

        // JDK HttpClient 的超时异常
        if (cause instanceof HttpConnectTimeoutException) {
            return new LlmApiException(LlmApiException.Type.TIMEOUT, url, cause);
        }
        if (cause instanceof HttpTimeoutException) {
            return new LlmApiException(LlmApiException.Type.TIMEOUT, url, cause);
        }

        // 传统 Socket 超时
        if (cause instanceof SocketTimeoutException) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains("connect")) {
                return new LlmApiException(LlmApiException.Type.TIMEOUT, url, cause);
            }
            return new LlmApiException(LlmApiException.Type.TIMEOUT, url, cause);
        }

        // DNS / 连接被拒
        if (cause instanceof UnknownHostException
                || cause instanceof ConnectException) {
            return new LlmApiException(LlmApiException.Type.NETWORK_ERROR, url, cause);
        }

        // 兜底
        return new LlmApiException(LlmApiException.Type.NETWORK_ERROR, url, cause);
    }
}
