package com.hify.module.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.common.web.UserContext;
import com.hify.module.workflow.controller.dto.WorkflowExecutionCreateRequest;
import com.hify.module.workflow.controller.dto.WorkflowExecutionResponse;
import com.hify.module.workflow.service.WorkflowExecutionService;
import com.hify.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流执行控制器.
 */
@RestController
@RequestMapping("/api/v1/workflows/{workflowId}/executions")
@RequiredArgsConstructor
public class WorkflowExecutionController {

    private final WorkflowExecutionService workflowExecutionService;
    private final WorkflowService workflowService;

    @GetMapping
    public PageResult<WorkflowExecutionResponse> list(
            @PathVariable Long workflowId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<WorkflowExecutionResponse> result = workflowExecutionService.pageByWorkflow(
                workflowId,
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<WorkflowExecutionResponse> get(@PathVariable Long workflowId, @PathVariable Long id) {
        return Result.ok(workflowExecutionService.getById(id));
    }

    @PostMapping
    public Result<WorkflowExecutionResponse> execute(
            @PathVariable Long workflowId,
            @RequestBody(required = false) WorkflowExecutionCreateRequest request) {
        Long userId = UserContext.getUserId();
        var workflow = workflowService.getById(workflowId);
        String inputData = request != null ? request.getInputData() : null;
        var execution = workflowExecutionService.create(workflowId, workflow.getVersion(), inputData, userId);
        // MVP: 直接标记为完成，实际执行逻辑后续异步实现
        workflowExecutionService.updateStatus(execution.getId(), "RUNNING", null);
        workflowExecutionService.complete(execution.getId(), "{}");
        return Result.ok(workflowExecutionService.getById(execution.getId()));
    }
}
