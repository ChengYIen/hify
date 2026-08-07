package com.hify.module.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;
import com.hify.module.conversation.service.ChatMessageService;
import com.hify.module.conversation.service.ChatSessionService;
import com.hify.module.conversation.service.ChatStreamService;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.LlmStreamCallback;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对话消息发送业务实现（流式 + 阻塞双模式）.
 *
 * <p>SSE 数据行为类型化事件：{@code {"type":"delta","content":...}} 逐段推送，
 * 结束时 {@code {"type":"done","finishReason":...,"latencyMs":...}}，
 * 出错 {@code {"type":"error","message":...}}。阻塞模式复用同一套编排，不走 SSE。</p>
 *
 * <p>线程模型：同步段在 Tomcat 线程只做校验 + 建 emitter + 提交任务，立即返回释放；
 * {@code llmExecutor} 线程跑持久化/历史组装/发起流式调用（enqueue 后立即返回）；
 * 增量回调与最终的助手消息持久化都在 OkHttp worker 线程上串行执行。</p>
 *
 * <p>心跳：流式期间每 10s 由 {@code heartbeatScheduler} 发送一个 SSE comment（{@code :ping}），
 * 防止网关因长时间无数据掐断长连接；流结束（成功/失败/同步异常）立即取消心跳。</p>
 *
 * <p>事务：本类不标注 {@code @Transactional}，持久化全部委托给
 * {@link ChatMessageServiceImpl} 自带独立事务的方法——事务绝不包裹 LLM 调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private static final int HISTORY_LIMIT = 20;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;
    /**
     * 必须大于最长 LLM 读取超时（Ollama 180s），否则长流会被 emitter 自身超时掐断；
     * 心跳保证连接持续有数据，超时只是兜底，实际很少触发。
     */
    private static final long EMITTER_TIMEOUT_MS = 300_000L;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final LlmProviderApi llmProviderApi;
    @Qualifier("llmExecutor")
    private final ThreadPoolTaskExecutor llmExecutor;
    @Qualifier("heartbeatScheduler")
    private final ScheduledThreadPoolExecutor heartbeatScheduler;
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter streamMessage(Long sessionId, String content) {
        // 同步校验：会话不存在时在返回 emitter 之前抛 BizException（GlobalExceptionHandler 统一转译）
        ChatSessionResponse session = chatSessionService.getById(sessionId);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> log.info("SSE 流结束: sessionId={}", sessionId));
        emitter.onTimeout(() -> log.warn("SSE 流超时: sessionId={}", sessionId));
        emitter.onError(e -> log.warn("SSE 客户端断开: sessionId={}, error={}", sessionId, e.getMessage()));

        llmExecutor.submit(() -> {
            try {
                doStream(session, content, emitter);
            } catch (Exception e) {
                // 异步线程边界：任何异常都必须保证 emitter 收敛，不允许悬挂到超时。
                // 此处故意用宽 catch —— 否则 RuntimeException（如 modelId 解析失败）会
                // 让连接挂到超时（有意覆盖 CLAUDE.md「禁止宽 catch」规则）。
                log.error("流式对话异常: sessionId={}", sessionId, e);
                sendEvent(emitter, Map.of("type", "error", "message", String.valueOf(e.getMessage())));
                emitter.complete();
            }
        });
        return emitter;
    }

    @Override
    public ChatMessageResponse sendBlocking(Long sessionId, String content) {
        // 同步校验：会话不存在时在调用 LLM 之前抛 BizException
        ChatSessionResponse session = chatSessionService.getById(sessionId);

        // 1. 持久化用户消息（独立事务，事务不包裹 LLM 调用）
        chatMessageService.createUserMessage(sessionId, content);

        // 2. 组装历史（与流式共用同一套）
        List<LlmRequestDTO.Message> messages = buildHistoryMessages(sessionId);

        // 3. 同步调用 LLM —— 阻塞模式客户端本就等结果，占用请求线程是语义要求
        LlmRequestDTO request = LlmRequestDTO.builder()
                .modelId(session.getModelId())
                .messages(messages)
                .stream(false)
                .build();
        LlmResponseDTO response = llmProviderApi.chat(request);
        log.info("LLM 阻塞对话完成: sessionId={}, modelId={}, latencyMs={}",
                sessionId, session.getModelId(), response.getLatencyMs());

        // 4. 持久化助手消息并返回（独立事务）
        return chatMessageService.createAssistantMessage(
                sessionId,
                response.getContent(),
                response.getModel(),
                toTokenUsageJson(response.getUsage()),
                response.getFinishReason(),
                response.getLatencyMs() != null ? response.getLatencyMs().intValue() : null);
    }

    private void doStream(ChatSessionResponse session, String content, SseEmitter emitter) {
        Long sessionId = session.getId();

        // 1. 持久化用户消息 —— createUserMessage 自带独立事务，无 LLM 调用（事务不包裹外部 IO）
        chatMessageService.createUserMessage(sessionId, content);

        // 2. 组装历史：listBySession 返回最新 N 条（seq DESC），反转成时间正序
        List<LlmRequestDTO.Message> messages = buildHistoryMessages(sessionId);

        // 3. modelId 为空时由 provider 层 resolveTarget 抛 BizException，落入外层 catch 转 error 事件
        LlmRequestDTO request = LlmRequestDTO.builder()
                .modelId(session.getModelId())
                .messages(messages)
                .stream(true)
                .build();

        // 4. 启动 SSE 心跳：LLM 推理期间若长时间无数据，Nginx/云 LB 可能掐断长连接
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter);

        // 5. 调用共享流式接口 —— delta 实时推给前端
        try {
            llmProviderApi.streamChat(request, new LlmStreamCallback() {
                @Override
                public void onContent(String delta) {
                    sendEvent(emitter, Map.of("type", "delta", "content", delta));
                }

                @Override
                public void onComplete(LlmResponseDTO response) {
                    // 流已结束：先取消心跳，再持久化 + 收尾
                    cancelHeartbeat(heartbeat);
                    persistAndFinish(sessionId, response, emitter);
                }

                @Override
                public void onError(String message) {
                    cancelHeartbeat(heartbeat);
                    log.warn("LLM 流式错误: sessionId={}, msg={}", sessionId, message);
                    sendEvent(emitter, Map.of("type", "error", "message", message));
                    emitter.complete();
                }
            });
        } catch (RuntimeException e) {
            // streamChat 同步抛异常（如 modelId 解析失败）：心跳必须取消，异常交给外层 catch 收敛 emitter
            cancelHeartbeat(heartbeat);
            throw e;
        }
    }

    /**
     * 先持久化助手消息（独立事务），再发送 done 帧 —— 客户端看到 done 时 DB 已可查询到完整回复.
     *
     * <p>done 帧对齐 Dify 的 {@code message_end} 语义：携带 messageId/model/latencyMs/usage
     * 元数据，前端可用 messageId 做点赞、删除等后续操作。流式 usage 当前为 null
     * （adapter 不累积），字段只在本帧铺好，等累积实现后自然透出。</p>
     */
    private void persistAndFinish(Long sessionId, LlmResponseDTO response, SseEmitter emitter) {
        ChatMessageResponse assistant = chatMessageService.createAssistantMessage(
                sessionId,
                response.getContent(),
                response.getModel(),
                toTokenUsageJson(response.getUsage()),
                response.getFinishReason(),
                response.getLatencyMs() != null ? response.getLatencyMs().intValue() : null);

        Map<String, Object> done = new HashMap<>();
        done.put("type", "done");
        if (response.getFinishReason() != null) {
            done.put("finishReason", response.getFinishReason());
        }
        if (response.getLatencyMs() != null) {
            done.put("latencyMs", response.getLatencyMs());
        }
        done.put("messageId", assistant.getId());
        if (response.getModel() != null) {
            done.put("model", response.getModel());
        }
        if (response.getUsage() != null) {
            done.put("usage", response.getUsage());
        }
        sendEvent(emitter, done);
        emitter.complete();
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception e) {
            // 客户端断开后 send 抛异常（emitter 已关闭/超时），记录并忽略，不再尝试
            log.warn("SSE 发送失败，可能客户端已断开: {}", e.getMessage());
        }
    }

    // ================================================================
    // SSE 心跳保活
    // ================================================================

    /**
     * 启动心跳：每 10s 向 emitter 发送一个 SSE comment（{@code :ping}）.
     *
     * <p>comment 行不产生事件，现有前端解析（只认 {@code data: } 行）完全不受影响；
     * 但对网关而言它仍是活跃数据，能阻止 Nginx/云 LB 掐断空闲长连接。</p>
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter) {
        return heartbeatScheduler.scheduleAtFixedRate(
                () -> sendPing(emitter),
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelHeartbeat(ScheduledFuture<?> heartbeat) {
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    private void sendPing(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (Exception e) {
            // 客户端已断开时发送失败：心跳失去意义，静默忽略（debug 级即可）
            log.debug("SSE 心跳发送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    /**
     * 组装发给 LLM 的历史消息：最新 N 条（seq DESC）反转成时间正序，映射为 shared DTO.
     */
    private List<LlmRequestDTO.Message> buildHistoryMessages(Long sessionId) {
        List<ChatMessageResponse> history = chatMessageService.listBySession(sessionId, HISTORY_LIMIT);
        Collections.reverse(history);
        return history.stream()
                .map(m -> LlmRequestDTO.Message.builder()
                        .role(m.getRole())
                        .content(m.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    private String toTokenUsageJson(LlmResponseDTO.TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(usage);
        } catch (JsonProcessingException e) {
            log.warn("tokenUsage 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
