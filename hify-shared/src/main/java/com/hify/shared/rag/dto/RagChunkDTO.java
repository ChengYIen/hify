package com.hify.shared.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 检索结果块 DTO（跨模块共享）.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagChunkDTO {

    /** 块 ID */
    private Long id;

    /** 原文内容 */
    private String content;

    /** 知识库 ID */
    private Long knowledgeId;

    /** 文档 ID */
    private Long documentId;

    /** 块序号（文档内从 0 开始） */
    private Integer chunkIndex;

    /** 相似度分数 */
    private Double score;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
