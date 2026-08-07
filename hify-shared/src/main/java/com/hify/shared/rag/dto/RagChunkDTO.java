package com.hify.shared.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /** 相似度分数 */
    private Double score;
}
