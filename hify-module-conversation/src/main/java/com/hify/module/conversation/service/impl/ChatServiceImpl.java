package com.hify.module.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.UserContext;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import com.hify.module.conversation.controller.dto.ChatSessionCreateRequest;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;
import com.hify.module.conversation.service.ChatMessageService;
import com.hify.module.conversation.service.ChatService;
import com.hify.module.conversation.service.ChatSessionService;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.LlmStreamCallback;
import com.hify.shared.llm.LlmStreamHandle;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import com.hify.shared.provider.ModelQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>异常处理：LLM 超时走 {@link SseEmitter#onTimeout} 回调（取消 LLM 调用并终止连接）；
 * 客户端断开时 {@code emitter.send()} 抛 {@link IOException}，catch 后取消 LLM 调用并
 * {@link SseEmitter#completeWithError} 收敛，避免继续消耗 token 或连接悬挂；
 * 所有 send 失败都收敛到 {@code completeWithError}。心跳每 10s 发送一个 SSE comment
 * 保活，防止网关因长时间无数据掐断长连接。</p>
 *
 * <p>事务：本类不标注 {@code @Transactional}——返回 SseEmitter 的方法绝不包裹事务，
 * 写消息操作拆成独立方法，全部委托给 {@link ChatMessageServiceImpl} 自带独立事务的
 * 方法。事务绝不包裹 LLM 调用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int HISTORY_LIMIT = 20;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 10;
    private static final int TITLE_MAX_LENGTH = 30;
    /**
     * 必须大于最长 LLM 读取超时（Ollama 180s），否则长流会被 emitter 自身超时掐断；
     * 心跳保证连接持续有数据，超时只是兜底，实际很少触发。
     */
    private static final long EMITTER_TIMEOUT_MS = 300_000L;

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final LlmProviderApi llmProviderApi;
    private final ModelQueryApi modelQueryApi;
    @Qualifier("llmExecutor")
    private final ThreadPoolTaskExecutor llmExecutor;
    @Qualifier("heartbeatScheduler")
    private final ScheduledThreadPoolExecutor heartbeatScheduler;
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter sendMessage(Long sessionId, String content) {
        // 同步校验（Tomcat 线程）：会话不存在时在返回 emitter 之前抛 BizException；
        // sessionId 为 null 时自动创建新会话（取第一个可用模型）。
        ChatSessionResponse session = resolveOrCreateSession(sessionId, content);
        Long resolvedSessionId = session.getId();

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        StreamState state = new StreamState(emitter, resolvedSessionId);

        emitter.onCompletion(() -> log.info("SSE 流结束: sessionId={}", resolvedSessionId));
        // LLM 超时（长时间无数据，心跳兜底后仍超时）：取消 LLM 调用并终止连接
        emitter.onTimeout(() -> {
            log.warn("SSE 流超时，取消 LLM 调用: sessionId={}", resolvedSessionId);
            cancelLlmCall(state);
            sendEvent(state, Map.of("type", "error", "message", "对话超时，已中断"));
            finishWithError(state, ErrorCode.CONVERSATION_TIMEOUT, "对话超时，已中断");
        });
        emitter.onError(e -> {
            log.warn("SSE 客户端断开: sessionId={}, error={}", resolvedSessionId, e.getMessage());
            cancelLlmCall(state);
        });

        llmExecutor.submit(() -> {
            try {
                doStream(session, content, state);
            } catch (Exception e) {
                // 异步线程边界：任何异常都必须保证 emitter 收敛，不允许悬挂到超时。
                // 此处故意用宽 catch —— 否则 RuntimeException（如 modelId 解析失败）会
                // 让连接挂到超时（有意覆盖 CLAUDE.md「禁止宽 catch」规则）。
                log.error("流式对话异常: sessionId={}", resolvedSessionId, e);
                sendEvent(state, Map.of("type", "error", "message", String.valueOf(e.getMessage())));
                finish(state);
            }
        });
        return emitter;
    }

    @Override
    public ChatMessageResponse sendBlocking(Long sessionId, String content) {
        // 同步校验：会话不存在时在调用 LLM 之前抛 BizException
        ChatSessionResponse session = resolveOrCreateSession(sessionId, content);

        // 1. 持久化用户消息（独立事务，事务不包裹 LLM 调用）
        chatMessageService.createUserMessage(session.getId(), content);

        // 2. 组装历史（与流式共用同一套）
        List<LlmRequestDTO.Message> messages = buildHistoryMessages(session.getId());

        // 3. 同步调用 LLM —— 阻塞模式客户端本就等结果，占用请求线程是语义要求
        LlmRequestDTO request = LlmRequestDTO.builder()
                .modelId(session.getModelId())
                .messages(messages)
                .stream(false)
                .build();
        LlmResponseDTO response = llmProviderApi.chat(request);
        log.info("LLM 阻塞对话完成: sessionId={}, modelId={}, latencyMs={}",
                session.getId(), session.getModelId(), response.getLatencyMs());

        // 4. 持久化助手消息并返回（独立事务）
        return chatMessageService.createAssistantMessage(
                session.getId(),
                response.getContent(),
                response.getModel(),
                toTokenUsageJson(response.getUsage()),
                response.getFinishReason(),
                response.getLatencyMs() != null ? response.getLatencyMs().intValue() : null);
    }

    // ================================================================
    // 异步流式编排
    // ================================================================

    private void doStream(ChatSessionResponse session, String content, StreamState state) {
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
        startHeartbeat(state);

        // 5. 调用共享流式接口 —— delta 实时推给前端，句柄存起来供取消
        try {
            LlmStreamHandle handle = llmProviderApi.streamChat(request, new LlmStreamCallback() {
                @Override
                public void onContent(String delta) {
                    sendEvent(state, Map.of("type", "delta", "content", delta));
                }

                @Override
                public void onComplete(LlmResponseDTO response) {
                    cancelHeartbeat(state);
                    persistAndFinish(sessionId, response, state);
                }

                @Override
                public void onError(String message) {
                    cancelHeartbeat(state);
                    log.warn("LLM 流式错误: sessionId={}, msg={}", sessionId, message);
                    sendEvent(state, Map.of("type", "error", "message", message));
                    finish(state);
                }
            });
            state.llmHandle.set(handle);
        } catch (RuntimeException e) {
            // streamChat 同步抛异常（如 modelId 解析失败）：心跳必须取消，异常交给外层 catch 收敛 emitter
            cancelHeartbeat(state);
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
    private void persistAndFinish(Long sessionId, LlmResponseDTO response, StreamState state) {
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
        sendEvent(state, done);
        finish(state);
    }

    /**
     * 推送一条 SSE 事件.
     *
     * <p>发送失败分两类处理：{@link IOException} 说明客户端已断开——取消 LLM 调用
     * （不再消耗 token）并 {@code completeWithError} 收敛；其他异常（emitter 已关闭等）
     * 同样收敛，保证连接不悬挂。成功后 {@code finished} 标志阻止重复推送。</p>
     */
    private void sendEvent(StreamState state, Map<String, Object> data) {
        if (state.finished.get()) {
            return; // 已收敛，忽略后续推送
        }
        try {
            state.emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            // 客户端断开：取消 LLM 调用 + completeWithError 终止 SSE 连接
            log.warn("SSE 发送失败，客户端已断开，已取消 LLM 调用: sessionId={}, err={}",
                    state.sessionId, e.getMessage());
            cancelLlmCall(state);
            finishWithError(state, ErrorCode.SYSTEM_ERROR, "客户端已断开");
        } catch (Exception e) {
            log.warn("SSE 发送异常，已终止连接: sessionId={}, err={}", state.sessionId, e.getMessage());
            cancelLlmCall(state);
            finishWithError(state, ErrorCode.SYSTEM_ERROR, "发送失败: " + e.getMessage());
        }
    }

    // ================================================================
    // 会话解析
    // ================================================================

    /**
     * 解析目标会话；sessionId 为 null 时自动创建新会话.
     *
     * <p>新会话：模型取第一个可用模型（模型启用且提供商启用），标题取首条消息摘要。
     * 创建失败（无可用模型 / 未登录）抛 {@link BizException}。</p>
     */
    private ChatSessionResponse resolveOrCreateSession(Long sessionId, String content) {
        if (sessionId != null) {
            return chatSessionService.getById(sessionId); // 不存在时抛 BizException(CONVERSATION_NOT_FOUND)
        }
        Long defaultModelId = modelQueryApi.getFirstEnabledModelId();
        if (defaultModelId == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "暂无可用模型，请先在模型管理中配置并启用");
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法创建会话");
        }
        ChatSessionCreateRequest request = new ChatSessionCreateRequest();
        request.setTitle(buildTitle(content));
        request.setModelId(defaultModelId);
        return chatSessionService.create(request, userId);
    }

    /** 新会话标题：取内容单行化后的前 30 字符. */
    private String buildTitle(String content) {
        String singleLine = content.strip().replaceAll("\\s+", " ");
        return singleLine.length() <= TITLE_MAX_LENGTH
                ? singleLine
                : singleLine.substring(0, TITLE_MAX_LENGTH);
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

    // ================================================================
    // SSE 心跳保活
    // ================================================================

    /**
     * 启动心跳：每 10s 向 emitter 发送一个 SSE comment（{@code :ping}）.
     *
     * <p>comment 行不产生事件，前端解析（只认 {@code data: } 行）完全不受影响；
     * 但对网关而言它仍是活跃数据，能阻止 Nginx/云 LB 掐断空闲长连接。</p>
     */
    private void startHeartbeat(StreamState state) {
        state.heartbeat = heartbeatScheduler.scheduleAtFixedRate(
                () -> sendPing(state),
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelHeartbeat(StreamState state) {
        ScheduledFuture<?> heartbeat = state.heartbeat;
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    private void sendPing(StreamState state) {
        if (state.finished.get()) {
            return;
        }
        try {
            state.emitter.send(SseEmitter.event().comment("ping"));
        } catch (IOException e) {
            // 客户端已断开时发送失败：取消 LLM 调用并收敛连接
            log.debug("SSE 心跳发送失败（客户端可能已断开），取消 LLM 调用: {}", e.getMessage());
            cancelLlmCall(state);
            finishWithError(state, ErrorCode.SYSTEM_ERROR, "客户端已断开");
        } catch (Exception e) {
            log.debug("SSE 心跳发送异常: {}", e.getMessage());
        }
    }

    // ================================================================
    // 收敛控制
    // ================================================================

    /**
     * 取消进行中的 LLM 调用（幂等，句柄未就绪时跳过）.
     */
    private void cancelLlmCall(StreamState state) {
        LlmStreamHandle handle = state.llmHandle.get();
        if (handle != null) {
            handle.cancel();
        }
    }

    /** 正常收敛：取消心跳并 complete. 幂等（finished CAS）。 */
    private void finish(StreamState state) {
        if (state.finished.compareAndSet(false, true)) {
            cancelHeartbeat(state);
            state.emitter.complete();
        }
    }

    /** 异常收敛：取消心跳并 completeWithError. 幂等（finished CAS）。 */
    private void finishWithError(StreamState state, ErrorCode code, String message) {
        if (state.finished.compareAndSet(false, true)) {
            cancelHeartbeat(state);
            state.emitter.completeWithError(new BizException(code, message));
        }
    }

    /**
     * 单次流式请求的状态 —— 跨 Tomcat / llmExecutor / OkHttp worker 线程共享.
     *
     * <p>{@code finished} 用 CAS 保证收敛动作只执行一次：complete / completeWithError /
     * 心跳取消 / LLM 取消 在多个线程都可能触发，必须互斥。</p>
     */
    private static final class StreamState {
        final SseEmitter emitter;
        final Long sessionId;
        final AtomicReference<LlmStreamHandle> llmHandle = new AtomicReference<>();
        final AtomicBoolean finished = new AtomicBoolean(false);
        volatile ScheduledFuture<?> heartbeat;

        StreamState(SseEmitter emitter, Long sessionId) {
            this.emitter = emitter;
            this.sessionId = sessionId;
        }
    }
}
