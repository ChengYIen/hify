package com.hify.module.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.knowledge.controller.dto.DocumentChunkResponse;
import com.hify.module.knowledge.controller.dto.KnowledgeDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档业务接口.
 */
public interface KnowledgeDocumentService {

    IPage<KnowledgeDocumentResponse> pageByKnowledge(Long knowledgeId, int page, int size);

    KnowledgeDocumentResponse getById(Long id);

    /**
     * 上传文档：校验、落盘、建 PENDING 记录后立即返回 documentId，处理在异步线程池执行.
     */
    Long upload(Long knowledgeId, MultipartFile file);

    /**
     * 查询文档分块列表（pgvector 数据源）.
     */
    List<DocumentChunkResponse> listChunks(Long documentId);

    void delete(Long id);

    /**
     * 异步索引文档内容：切块 → Embedding → 写入 pgvector.
     * <p>方法立即返回，实际索引在 asyncExecutor 异步执行，结果通过文档 status 反映
     * （PROCESSING → DONE / FAILED）。</p>
     *
     * @param documentId 文档 ID
     * @param content    文档原始文本（由解析器产出）
     */
    void indexContent(Long documentId, String content);
}
