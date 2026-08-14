package com.hify.module.workflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.Result;
import com.hify.module.workflow.controller.dto.WorkflowNodeRunDetailResponse;
import com.hify.module.workflow.controller.dto.WorkflowRunDetailResponse;
import com.hify.module.workflow.repository.WorkflowNodeRunMapper;
import com.hify.module.workflow.repository.WorkflowRunMapper;
import com.hify.module.workflow.repository.entity.WorkflowNodeRunEntity;
import com.hify.module.workflow.repository.entity.WorkflowRunEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only queries for workflow engine run records.
 */
@RestController
@RequestMapping("/api/v1/workflows/{workflowId}/runs")
@RequiredArgsConstructor
public class WorkflowRunController {

    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;

    @GetMapping("/latest")
    public Result<WorkflowRunDetailResponse> latest(@PathVariable Long workflowId) {
        List<WorkflowRunEntity> runs = workflowRunMapper.selectList(
                new LambdaQueryWrapper<WorkflowRunEntity>()
                        .eq(WorkflowRunEntity::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowRunEntity::getId));
        if (runs.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "工作流尚无执行记录: workflowId=" + workflowId);
        }

        WorkflowRunEntity run = runs.get(0);
        List<WorkflowNodeRunEntity> nodeRuns = workflowNodeRunMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeRunEntity>()
                        .eq(WorkflowNodeRunEntity::getWorkflowRunId, run.getId())
                        .orderByAsc(WorkflowNodeRunEntity::getId));

        List<WorkflowNodeRunDetailResponse> nodeResponses = nodeRuns.stream()
                .map(this::toNodeResponse)
                .toList();
        return Result.ok(WorkflowRunDetailResponse.builder()
                .id(run.getId())
                .workflowId(run.getWorkflowId())
                .status(run.getStatus())
                .input(run.getInput())
                .output(run.getOutput())
                .error(run.getError())
                .elapsedMs(run.getElapsedMs())
                .createdAt(run.getCreatedAt())
                .finishedAt(run.getFinishedAt())
                .nodeRuns(nodeResponses)
                .build());
    }

    private WorkflowNodeRunDetailResponse toNodeResponse(WorkflowNodeRunEntity entity) {
        return WorkflowNodeRunDetailResponse.builder()
                .id(entity.getId())
                .workflowRunId(entity.getWorkflowRunId())
                .nodeKey(entity.getNodeKey())
                .nodeType(entity.getNodeType())
                .status(entity.getStatus())
                .outputs(entity.getOutputs())
                .error(entity.getError())
                .elapsedMs(entity.getElapsedMs())
                .createdAt(entity.getCreatedAt())
                .finishedAt(entity.getFinishedAt())
                .build();
    }
}
