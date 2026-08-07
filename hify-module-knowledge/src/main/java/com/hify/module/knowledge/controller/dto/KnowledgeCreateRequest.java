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

    private String embeddingModel;
}
