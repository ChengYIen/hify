package com.hify.module.workflow.model;

/**
 * Sealed parent type for all node configs.
 *
 * <p>Each node type owns exactly one record. {@link NodeConfigParser}
 * dispatches on the node-level {@code type} field and parses the config JSON
 * into the matching record, so the stored config object stays free of a
 * redundant {@code type} property.</p>
 */
public sealed interface NodeConfig permits StartNodeConfig, LlmNodeConfig, ConditionNodeConfig,
        ApiCallNodeConfig, KnowledgeNodeConfig, EndNodeConfig {
}
