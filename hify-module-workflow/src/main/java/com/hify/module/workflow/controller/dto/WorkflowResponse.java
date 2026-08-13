package com.hify.module.workflow.controller.dto;

import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流响应体.
 */
@Data
@Builder
public class WorkflowResponse {

    private Long id;
    private String name;
    private String description;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<WorkflowNode> nodes;
    private List<WorkflowEdge> edges;
}
