package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Config for API_CALL nodes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiCallNodeConfig(String url, String method, Map<String, String> headers,
                                String outputVariable)
        implements NodeConfig {
}
