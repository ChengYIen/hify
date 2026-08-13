package com.hify.module.knowledge.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文档内容索引入参 —— 由解析器/前端提供原始文本，触发切块→Embedding→写 pgvector.
 */
@Data
public class DocumentContentRequest {

    @NotBlank(message = "文档内容不能为空")
    private String content;
}
