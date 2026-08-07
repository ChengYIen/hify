package com.hify.module.knowledge.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体.
 * <p>
 * 对应表 {@code hify_knowledge_document}。记录上传到知识库的原始文档信息，
 * 文档解析后将内容切块存入 PostgreSQL pgvector。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hify_knowledge_document")
public class KnowledgeDocumentEntity extends BaseEntity {

    /** 所属知识库 ID */
    private Long knowledgeId;

    /** 原始文件名 */
    private String filename;

    /** 文件类型：PDF / TXT / MD / DOCX */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件存储 URL */
    private String fileUrl;

    /** 解析状态：PENDING / PARSING / COMPLETED / FAILED */
    private String parseStatus;

    /** 切分块数 */
    private Integer chunkCount;

    /** 解析失败原因 */
    private String errorMessage;
}
