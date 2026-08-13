package com.hify.module.knowledge.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块响应体.
 */
@Data
@Builder
public class DocumentChunkResponse {

    private Long id;
    private Long knowledgeId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private LocalDateTime createdAt;
}
