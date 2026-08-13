package com.hify.module.workflow.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Connection between two nodes.
 *
 * <p>{@code condition} is the optional routing label on the edge. A null
 * condition means the edge is unconditional; a CONDITION node can fan out to
 * different targets with labels such as 售前 / 售后 / 技术支持.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowEdge {

    /** Edge id used by the canvas editor; may be null. */
    private String id;

    /** Source node key. */
    private String sourceNodeKey;

    /** Target node key. */
    private String targetNodeKey;

    /** Routing condition label; null means unconditional edge. */
    private String condition;
}
