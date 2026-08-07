package com.hify.module.conversation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import com.hify.module.conversation.controller.dto.SendMessageRequest;
import com.hify.module.conversation.service.ChatMessageService;
import com.hify.module.conversation.service.ChatStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话消息控制器.
 */
@RestController
@RequestMapping("/api/v1/chat/sessions/{sessionId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final ChatStreamService chatStreamService;

    @GetMapping
    public PageResult<ChatMessageResponse> list(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<ChatMessageResponse> result = chatMessageService.pageBySession(
                sessionId,
                page != null ? page : 1,
                pageSize != null ? pageSize : 50);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/latest")
    public Result<List<ChatMessageResponse>> latest(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(chatMessageService.listBySession(sessionId, limit));
    }

    /**
     * 发送消息.
     *
     * <p>{@code stream=true} 返回 SSE emitter（{@code delta}/{@code done}/{@code error} 类型化事件）；
     * {@code stream=false} 或缺省则同步阻塞，返回完整助手消息。</p>
     */
    @PostMapping
    public Object send(@PathVariable Long sessionId,
                       @Valid @RequestBody SendMessageRequest request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            return chatStreamService.streamMessage(sessionId, request.getContent());
        }
        return Result.ok(chatStreamService.sendBlocking(sessionId, request.getContent()));
    }
}
