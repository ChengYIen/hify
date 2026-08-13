package com.hify.module.workflow.controller.dto;

import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import lombok.Data;

import java.util.List;

/**
 * 更新工作流请求.
 */
@Data
public class WorkflowUpdateRequest {

    private String name;

    private String description;

    private List<WorkflowNode> nodes;

    private List<WorkflowEdge> edges;

    private String status;
}
