package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Config for KNOWLEDGE retrieval nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeNodeConfig(Long knowledgeBaseId, String query, Integer topK,
                                  String outputVariable) implements NodeConfig {
}
