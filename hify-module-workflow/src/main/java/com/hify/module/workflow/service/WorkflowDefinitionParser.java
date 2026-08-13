package com.hify.module.workflow.service;

import com.hify.module.workflow.model.WorkflowDefinition;

/**
 * Parses and validates workflow definitions.
 */
public interface WorkflowDefinitionParser {

    WorkflowDefinition parse(String json);

    void validate(WorkflowDefinition definition);
}
