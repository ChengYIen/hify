package com.hify.module.knowledge.controller.dto;

import lombok.Data;

/**
 * 更新知识库请求.
 */
@Data
public class KnowledgeUpdateRequest {

    private String name;

    private String description;
}
