package com.hify.module.workflow.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete workflow definition used as the API shape and validation container.
 * Nodes and edges are persisted separately in
 * {@code hify_workflow_node} / {@code hify_workflow_edge} and reassembled here.
 */
@Data
public class WorkflowDefinition {

    /** Schema version for future migrations of old definitions. */
    private String schemaVersion = "1.0";

    /** All nodes in the workflow. */
    private List<WorkflowNode> nodes = new ArrayList<>();

    /** Connections between nodes: source + sourcePort -> target + targetPort. */
    private List<WorkflowEdge> edges = new ArrayList<>();
}
