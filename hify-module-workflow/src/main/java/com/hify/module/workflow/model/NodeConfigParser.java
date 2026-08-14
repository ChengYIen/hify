package com.hify.module.workflow.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Dispatches on the node type and parses the config JSON into the matching
 * {@link NodeConfig} record.
 */
@Component
public final class NodeConfigParser {

    private final ObjectMapper objectMapper;

    public NodeConfigParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NodeConfig parse(WorkflowNodeType type, String configJson) throws JsonProcessingException {
        return switch (type) {
            case START -> objectMapper.readValue(configJson, StartNodeConfig.class);
            case LLM -> objectMapper.readValue(configJson, LlmNodeConfig.class);
            case CONDITION -> objectMapper.readValue(configJson, ConditionNodeConfig.class);
            case API_CALL -> objectMapper.readValue(configJson, ApiCallNodeConfig.class);
            case KNOWLEDGE -> objectMapper.readValue(configJson, KnowledgeNodeConfig.class);
            case END -> objectMapper.readValue(configJson, EndNodeConfig.class);
        };
    }
}
