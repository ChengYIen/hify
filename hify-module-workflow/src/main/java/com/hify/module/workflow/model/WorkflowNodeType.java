package com.hify.module.workflow.model;

/**
 * Workflow node types. The JSON field {@code type} uses the enum name,
 * for example {@code "LLM"}.
 */
public enum WorkflowNodeType {
    START,
    END,
    LLM,
    CONDITION,
    API_CALL,
    KNOWLEDGE
}
