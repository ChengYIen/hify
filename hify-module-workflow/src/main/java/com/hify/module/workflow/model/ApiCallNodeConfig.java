package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Config for API_CALL nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiCallNodeConfig(String url, String method, String outputVariable)
        implements NodeConfig {
}
