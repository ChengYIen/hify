package com.hify.module.knowledge.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库元数据实体.
 * <p>
 * 对应表 {@code hify_knowledge}（MySQL 侧）。仅存元数据，
 * 向量数据存在 PostgreSQL pgvector 的 {@code knowledge_chunk} 表中。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_knowledge")
public class KnowledgeEntity extends BaseEntity {

    /** 知识库名称 */
    private String name;

    /** 知识库描述 */
    private String description;

    /** 文档数量（冗余） */
    private Integer docCount;

    /** 向量块总数（冗余） */
    private Integer chunkCount;

    /** 使用的 Embedding 模型 */
    private String embeddingModel;

    /** 创建人用户 ID */
    private Long createdBy;
}
