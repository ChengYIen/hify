package com.hify.module.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.module.workflow.controller.dto.WorkflowCreateRequest;
import com.hify.module.workflow.controller.dto.WorkflowResponse;
import com.hify.module.workflow.controller.dto.WorkflowUpdateRequest;

/**
 * 工作流定义业务接口.
 */
public interface WorkflowService {

    IPage<WorkflowResponse> page(int page, int pageSize);

    WorkflowResponse getById(Long id);

    WorkflowResponse create(WorkflowCreateRequest request, Long userId);

    WorkflowResponse update(Long id, WorkflowUpdateRequest request);

    WorkflowResponse publish(Long id);

    WorkflowResponse disable(Long id);

    void delete(Long id);
}
