package com.hify.module.knowledge.controller;

import com.hify.common.web.Result;
import com.hify.module.knowledge.controller.dto.DocumentChunkResponse;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;
import com.hify.module.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档控制器（跨知识库的单文档操作）.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping("/{id}")
    public Result<KnowledgeDocumentResponse> get(@PathVariable Long id) {
        return Result.ok(knowledgeDocumentService.getById(id));
    }

    @GetMapping("/{id}/chunks")
    public Result<List<DocumentChunkResponse>> chunks(@PathVariable Long id) {
        return Result.ok(knowledgeDocumentService.listChunks(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeDocumentService.delete(id);
        return Result.ok();
    }
}
