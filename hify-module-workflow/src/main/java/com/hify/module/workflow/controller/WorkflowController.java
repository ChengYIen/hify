package com.hify.module.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.common.web.UserContext;
import com.hify.module.workflow.controller.dto.WorkflowCreateRequest;
import com.hify.module.workflow.controller.dto.WorkflowResponse;
import com.hify.module.workflow.controller.dto.WorkflowUpdateRequest;
import com.hify.module.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流控制器.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public PageResult<WorkflowResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        IPage<WorkflowResponse> result = workflowService.page(
                page != null ? page : 1,
                pageSize != null ? pageSize : 20);
        return PageHelper.toPageResult(result);
    }

    @GetMapping("/{id}")
    public Result<WorkflowResponse> get(@PathVariable Long id) {
        return Result.ok(workflowService.getById(id));
    }

    @PostMapping
    public Result<WorkflowResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        Long userId = UserContext.getUserId();
        return Result.ok(workflowService.create(request, userId));
    }

    @PutMapping("/{id}")
    public Result<WorkflowResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody WorkflowUpdateRequest request) {
        return Result.ok(workflowService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public Result<WorkflowResponse> publish(@PathVariable Long id) {
        return Result.ok(workflowService.publish(id));
    }

    @PutMapping("/{id}/disable")
    public Result<WorkflowResponse> disable(@PathVariable Long id) {
        return Result.ok(workflowService.disable(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }
}
