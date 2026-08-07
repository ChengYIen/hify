package com.hify.module.workflow.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建工作流请求.
 */
@Data
public class WorkflowCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "工作流定义不能为空")
    private String definition;
}
