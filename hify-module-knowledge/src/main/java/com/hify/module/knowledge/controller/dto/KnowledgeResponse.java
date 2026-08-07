package com.hify.module.knowledge.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库响应体.
 */
@Data
@Builder
public class KnowledgeResponse {

    private Long id;
    private String name;
    private String description;
    private Integer docCount;
    private Integer chunkCount;
    private String embeddingModel;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
