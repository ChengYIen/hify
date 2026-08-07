package com.hify.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.controller.dto.WorkflowExecutionResponse;
import com.hify.module.workflow.repository.WorkflowExecutionMapper;
import com.hify.module.workflow.repository.entity.WorkflowExecutionEntity;
import com.hify.module.workflow.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 工作流执行记录业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowExecutionMapper workflowExecutionMapper;

    @Override
    public IPage<WorkflowExecutionResponse> pageByWorkflow(Long workflowId, int page, int pageSize) {
        Page<WorkflowExecutionEntity> p = new Page<>(page, pageSize);
        Page<WorkflowExecutionEntity> result = workflowExecutionMapper.selectPage(p,
                new LambdaQueryWrapper<WorkflowExecutionEntity>()
                        .eq(WorkflowExecutionEntity::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowExecutionEntity::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public WorkflowExecutionResponse getById(Long id) {
        WorkflowExecutionEntity entity = workflowExecutionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "执行记录不存在: id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResponse create(Long workflowId, Integer workflowVersion,
                                             String inputData, Long triggeredBy) {
        WorkflowExecutionEntity entity = new WorkflowExecutionEntity();
        entity.setWorkflowId(workflowId);
        entity.setWorkflowVersion(workflowVersion != null ? workflowVersion : 1);
        entity.setInputData(inputData);
        entity.setStatus("PENDING");
        entity.setTriggeredBy(triggeredBy);
        workflowExecutionMapper.insert(entity);
        log.info("WorkflowExecution 创建成功: id={}, workflowId={}", entity.getId(), workflowId);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResponse updateStatus(Long id, String status, String errorMessage) {
        WorkflowExecutionEntity entity = workflowExecutionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "执行记录不存在: id=" + id);
        }
        entity.setStatus(status);
        if (errorMessage != null) {
            entity.setErrorMessage(errorMessage);
        }
        if ("RUNNING".equals(status) && entity.getStartedAt() == null) {
            entity.setStartedAt(LocalDateTime.now());
        }
        workflowExecutionMapper.updateById(entity);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResponse complete(Long id, String outputData) {
        WorkflowExecutionEntity entity = workflowExecutionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "执行记录不存在: id=" + id);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus("COMPLETED");
        entity.setOutputData(outputData);
        entity.setFinishedAt(now);
        if (entity.getStartedAt() != null) {
            entity.setDurationMs((int) java.time.Duration.between(entity.getStartedAt(), now).toMillis());
        }
        workflowExecutionMapper.updateById(entity);
        log.info("WorkflowExecution 完成: id={}, durationMs={}", id, entity.getDurationMs());
        return toResponse(entity);
    }

    private WorkflowExecutionResponse toResponse(WorkflowExecutionEntity entity) {
        return WorkflowExecutionResponse.builder()
                .id(entity.getId())
                .workflowId(entity.getWorkflowId())
                .workflowVersion(entity.getWorkflowVersion())
                .inputData(entity.getInputData())
                .outputData(entity.getOutputData())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .durationMs(entity.getDurationMs())
                .triggeredBy(entity.getTriggeredBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
