package com.hify.module.knowledge.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建知识库请求.
 */
@Data
public class KnowledgeCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    /**
     * Embedding 模型配置 ID（关联 hify_provider_model.id，modelType=EMBEDDING）.
     * 不传时自动选用第一个可用的 Embedding 模型。
     */
    private Long embeddingModelId;
}
