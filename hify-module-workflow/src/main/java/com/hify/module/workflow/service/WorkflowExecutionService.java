package com.hify.module.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.workflow.controller.dto.WorkflowExecutionResponse;

/**
 * 工作流执行记录业务接口.
 */
public interface WorkflowExecutionService {

    IPage<WorkflowExecutionResponse> pageByWorkflow(Long workflowId, int page, int pageSize);

    WorkflowExecutionResponse getById(Long id);

    WorkflowExecutionResponse create(Long workflowId, Integer workflowVersion, String inputData, Long triggeredBy);

    WorkflowExecutionResponse updateStatus(Long id, String status, String errorMessage);

    WorkflowExecutionResponse complete(Long id, String outputData);
}
