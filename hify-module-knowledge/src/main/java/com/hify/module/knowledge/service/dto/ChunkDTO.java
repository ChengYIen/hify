package com.hify.module.knowledge.service.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文档分块管线 DTO：分块产出，向量化后补 embedding.
 */
@Data
@Builder
public class ChunkDTO {

    /** 块序号（文档内从 0 开始） */
    private Integer chunkIndex;

    /** 块原文 */
    private String content;

    /** 块 token 估算值 */
    private Integer tokenCount;

    /** pgvector 向量字面量（如 "[0.1,0.2,...]"），embedChunks 后填充 */
    private String embedding;
}
