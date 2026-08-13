package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

/**
 * Common fields shared by every workflow node.
 *
 * <p>Node-specific configuration lives in the polymorphic {@link #config}
 * field. {@link WorkflowNodeDeserializer} reads the node-level {@code type}
 * property and {@link NodeConfigParser} deserializes {@code config} into the
 * matching record, for example {@link LlmNodeConfig} for {@code "type": "LLM"}.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonDeserialize(using = WorkflowNodeDeserializer.class)
public class WorkflowNode {

    /** Node key, unique within one workflow. Edges reference nodes by this key. */
    private String nodeKey;

    /** Display name used by the visual editor. */
    private String name;

    /** Canvas coordinates used by the editor; ignored at execution time. */
    private WorkflowNodePosition position;

    /** Node type; also the discriminator for {@link #config}. */
    private WorkflowNodeType type;

    private NodeConfig config;
}
