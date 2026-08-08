package com.hify.module.conversation.controller;

import com.hify.common.web.Result;
import com.hify.module.conversation.controller.dto.SendMessageRequest;
import com.hify.module.conversation.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话控制器 —— 无会话直发消息.
 *
 * <p>{@code sessionId} 可选（body 中缺省时自动创建新会话），用于"新对话直接开聊"
 * 的场景；已有会话走 {@link ChatMessageController} 的
 * {@code /api/v1/chat/sessions/{sessionId}/messages}。</p>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 直发消息（sessionId 可选）.
     *
     * <p>{@code stream=true} 返回 SSE emitter（{@code delta}/{@code done}/{@code error}
     * 类型化事件）；{@code stream=false} 或缺省则同步阻塞，返回完整助手消息。
     * 返回类型为 {@link Object}：流式返回 {@link org.springframework.web.servlet.mvc.method.annotation.SseEmitter}，
     * 阻塞返回 {@code Result<ChatMessageResponse>}。</p>
     */
    @PostMapping("/messages")
    public Object send(@Valid @RequestBody SendMessageRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            return chatService.sendMessage(request.getSessionId(), request.getContent());
        }
        return Result.ok(chatService.sendBlocking(request.getSessionId(), request.getContent()));
    }
}
