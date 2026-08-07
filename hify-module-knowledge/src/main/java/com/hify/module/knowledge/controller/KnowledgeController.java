package com.hify.module.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.common.web.UserContext;
import com.hify.module.knowledge.controller.dto.KnowledgeCreateRequest;
import com.hify.module.knowledge.controller.dto.KnowledgeResponse;
import com.hify.module.knowledge.controller.dto.KnowledgeUpdateRequest;
import com.hify.module.knowledge.service.KnowledgeService;
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
 * 知识库控制器.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public PageResult<KnowledgeResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<KnowledgeResponse> result = knowledgeService.page(
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<KnowledgeResponse> get(@PathVariable Long id) {
        return Result.ok(knowledgeService.getById(id));
    }

    @PostMapping
    public Result<KnowledgeResponse> create(@Valid @RequestBody KnowledgeCreateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(knowledgeService.create(request, userId));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody KnowledgeUpdateRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }
}
