package com.hify.module.workflow.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Per-node execution detail for a workflow run.
 */
@Data
@Builder
public class WorkflowNodeRunDetailResponse {

    private Long id;
    private Long workflowRunId;
    private String nodeKey;
    private String nodeType;
    private String status;
    private String outputs;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
