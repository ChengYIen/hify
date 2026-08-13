package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Config for END nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EndNodeConfig(String outputTemplate) implements NodeConfig {
}
