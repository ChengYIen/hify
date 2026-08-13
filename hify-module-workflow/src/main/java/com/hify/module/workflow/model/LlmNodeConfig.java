package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Config for LLM nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmNodeConfig(Long modelConfigId, String prompt, String outputVariable)
        implements NodeConfig {
}
