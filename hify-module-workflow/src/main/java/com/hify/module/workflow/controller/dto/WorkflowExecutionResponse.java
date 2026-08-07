package com.hify.module.workflow.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流执行记录响应体.
 */
@Data
@Builder
public class WorkflowExecutionResponse {

    private Long id;
    private Long workflowId;
    private Integer workflowVersion;
    private String inputData;
    private String outputData;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer durationMs;
    private Long triggeredBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
