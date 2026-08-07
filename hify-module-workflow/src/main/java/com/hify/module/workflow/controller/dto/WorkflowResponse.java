package com.hify.module.workflow.controller.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流响应体.
 */
@Data
@Builder
public class WorkflowResponse {

    private Long id;
    private String name;
    private String description;
    private String definition;
    private String status;
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
