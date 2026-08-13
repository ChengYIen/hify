package com.hify.module.knowledge.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档响应体.
 */
@Data
@Builder
public class KnowledgeDocumentResponse {

    private Long id;
    private Long knowledgeId;
    private String filename;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private String status;
    private Integer chunkCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
