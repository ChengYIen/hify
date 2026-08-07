package com.hify.module.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;
import com.hify.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库文档控制器.
 */
@RestController
@RequestMapping("/api/v1/knowledge/{knowledgeId}/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping
    public PageResult<KnowledgeDocumentResponse> list(
            @PathVariable Long knowledgeId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<KnowledgeDocumentResponse> result = knowledgeDocumentService.pageByKnowledge(
                knowledgeId,
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<KnowledgeDocumentResponse> get(@PathVariable Long knowledgeId, @PathVariable Long id) {
        return Result.ok(knowledgeDocumentService.getById(id));
    }

    @PostMapping
    public Result<KnowledgeDocumentResponse> upload(
            @PathVariable Long knowledgeId,
            @RequestParam String filename,
            @RequestParam String fileType,
            @RequestParam Long fileSize,
            @RequestParam(required = false) String fileUrl) {
        return Result.ok(knowledgeDocumentService.create(knowledgeId, filename, fileType, fileSize, fileUrl));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long knowledgeId, @PathVariable Long id) {
        knowledgeDocumentService.delete(id);
        return Result.ok();
    }
}
