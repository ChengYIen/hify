package com.hify.module.workflow.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Latest workflow run detail with per-node execution records.
 */
@Data
@Builder
public class WorkflowRunDetailResponse {

    private Long id;
    private Long workflowId;
    private String status;
    private String input;
    private String output;
    private String error;
    private Integer elapsedMs;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private List<WorkflowNodeRunDetailResponse> nodeRuns;
}
