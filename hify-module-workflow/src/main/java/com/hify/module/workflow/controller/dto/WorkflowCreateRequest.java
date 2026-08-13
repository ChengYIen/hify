package com.hify.module.workflow.controller.dto;

import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建工作流请求.
 */
@Data
public class WorkflowCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    @NotEmpty(message = "节点不能为空")
    private List<WorkflowNode> nodes;

    @NotEmpty(message = "边不能为空")
    private List<WorkflowEdge> edges;
}
