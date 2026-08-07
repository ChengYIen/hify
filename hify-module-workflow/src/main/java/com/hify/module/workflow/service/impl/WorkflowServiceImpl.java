package com.hify.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.controller.dto.WorkflowCreateRequest;
import com.hify.module.workflow.controller.dto.WorkflowResponse;
import com.hify.module.workflow.controller.dto.WorkflowUpdateRequest;
import com.hify.module.workflow.repository.WorkflowMapper;
import com.hify.module.workflow.repository.entity.WorkflowEntity;
import com.hify.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作流定义业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;

    @Override
    public IPage<WorkflowResponse> page(int page, int pageSize) {
        Page<WorkflowEntity> p = new Page<>(page, pageSize);
        Page<WorkflowEntity> result = workflowMapper.selectPage(p,
                new LambdaQueryWrapper<WorkflowEntity>()
                        .orderByDesc(WorkflowEntity::getId));
        return result.convert(this::toResponse);
    }

    @Override
    public WorkflowResponse getById(Long id) {
        WorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse create(WorkflowCreateRequest request, Long userId) {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setDefinition(request.getDefinition());
        entity.setStatus("DRAFT");
        entity.setVersion(1);
        entity.setCreatedBy(userId);
        workflowMapper.insert(entity);
        log.info("Workflow 创建成功: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse update(Long id, WorkflowUpdateRequest request) {
        WorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND, "id=" + id);
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getDefinition() != null) {
            entity.setDefinition(request.getDefinition());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        workflowMapper.updateById(entity);
        log.info("Workflow 更新成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse publish(Long id) {
        WorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND, "id=" + id);
        }
        if (entity.getDefinition() == null || entity.getDefinition().isBlank()) {
            throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION, "定义不能为空");
        }
        entity.setStatus("PUBLISHED");
        entity.setVersion(entity.getVersion() + 1);
        workflowMapper.updateById(entity);
        log.info("Workflow 发布成功: id={}, version={}", id, entity.getVersion());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse disable(Long id) {
        WorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND, "id=" + id);
        }
        entity.setStatus("DISABLED");
        workflowMapper.updateById(entity);
        log.info("Workflow 禁用成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND, "id=" + id);
        }
        workflowMapper.deleteById(id);
        log.info("Workflow 删除成功: id={}", id);
    }

    private WorkflowResponse toResponse(WorkflowEntity entity) {
        return WorkflowResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .definition(entity.getDefinition())
                .status(entity.getStatus())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
