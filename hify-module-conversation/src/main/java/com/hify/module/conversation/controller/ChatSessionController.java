package com.hify.module.conversation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.common.web.UserContext;
import com.hify.module.conversation.controller.dto.ChatSessionCreateRequest;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;
import com.hify.module.conversation.service.ChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话会话控制器.
 */
@RestController
@RequestMapping("/api/v1/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping
    public PageResult<ChatSessionResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Long userId = UserContext.getUserId();
        IPage<ChatSessionResponse> result = chatSessionService.pageByUser(
                userId,
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<ChatSessionResponse> get(@PathVariable Long id) {
        return Result.ok(chatSessionService.getById(id));
    }

    @PostMapping
    public Result<ChatSessionResponse> create(@Valid @RequestBody ChatSessionCreateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(chatSessionService.create(request, userId));
    }

    @PutMapping("/{id}/archive")
    public Result<ChatSessionResponse> archive(@PathVariable Long id) {
        return Result.ok(chatSessionService.archive(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chatSessionService.delete(id);
        return Result.ok();
    }
}
