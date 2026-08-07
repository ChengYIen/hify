package com.hify.module.workflow.controller.dto;

import lombok.Data;

/**
 * 更新工作流请求.
 */
@Data
public class WorkflowUpdateRequest {

    private String name;

    private String description;

    private String definition;

    private String status;
}
