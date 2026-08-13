package com.hify.module.workflow.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Canvas position of a node, used only by the visual editor. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodePosition {

    private Double x;

    private Double y;
}
