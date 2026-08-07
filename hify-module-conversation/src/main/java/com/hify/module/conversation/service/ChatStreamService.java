package com.hify.module.conversation.service;

import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话消息发送业务接口（流式 + 阻塞双模式）.
 */
public interface ChatStreamService {

    /**
     * 以 SSE 流式发送一条用户消息，并实时推送助手回复增量.
     *
     * <p>方法立即返回 {@link SseEmitter}，实际对话在 {@code llmExecutor} 上异步执行。
     * 会话不存在时在返回 emitter 之前同步抛 {@link com.hify.common.exception.BizException}。</p>
     *
     * <p>SSE 数据行为类型化事件：{@code {"type":"delta","content":...}} /
     * {@code {"type":"done","finishReason":...,"latencyMs":...}} /
     * {@code {"type":"error","message":...}}。</p>
     *
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @return SSE emitter
     */
    SseEmitter streamMessage(Long sessionId, String content);

    /**
     * 以阻塞方式发送一条用户消息，返回完整助手回复.
     *
     * <p>同步执行 LLM 调用并持久化，客户端等待最终结果。不使用线程池——
     * 阻塞模式本身就要求客户端等结果，占用请求线程是语义要求。</p>
     *
     * @param sessionId 会话 ID
     * @param content   用户消息内容
     * @return 已持久化的助手消息
     */
    ChatMessageResponse sendBlocking(Long sessionId, String content);
}
