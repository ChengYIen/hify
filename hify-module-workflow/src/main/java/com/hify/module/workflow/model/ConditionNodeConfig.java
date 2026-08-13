package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Config for CONDITION nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConditionNodeConfig(String expression, String outputVariable)
        implements NodeConfig {
}
