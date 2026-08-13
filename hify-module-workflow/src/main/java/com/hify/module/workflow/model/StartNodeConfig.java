package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Config for START nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StartNodeConfig(Map<String, String> inputSchema) implements NodeConfig {
}
