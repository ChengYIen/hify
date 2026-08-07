package com.hify.module.conversation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.conversation.controller.dto.ChatMessageResponse;
import com.hify.module.conversation.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对话消息控制器.
 */
@RestController
@RequestMapping("/api/v1/chat-sessions/{sessionId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

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
}
