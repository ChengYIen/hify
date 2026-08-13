package com.hify.module.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.module.knowledge.controller.dto.DocumentContentRequest;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;
import com.hify.module.knowledge.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档控制器.
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases/{kbId}/documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @GetMapping
    public PageResult<KnowledgeDocumentResponse> list(
            @PathVariable Long kbId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        IPage<KnowledgeDocumentResponse> result = knowledgeDocumentService.pageByKnowledge(
                kbId,
                page != null ? page : 1,
                size != null ? size : 20);
        return PageHelper.toPageResult(result);
    }

    @PostMapping
    public Result<Long> upload(@PathVariable Long kbId,
                               @RequestParam("file") MultipartFile file) {
        return Result.ok(knowledgeDocumentService.upload(kbId, file));
    }

    /**
     * 索引文档内容（解析器产出原始文本后调用），触发异步切片 + Embedding + 写 pgvector.
     */
    @PostMapping("/{id}/content")
    public Result<Void> indexContent(@PathVariable Long id,
                                     @Valid @RequestBody DocumentContentRequest request) {
        knowledgeDocumentService.indexContent(id, request.getContent());
        return Result.ok();
    }
}
