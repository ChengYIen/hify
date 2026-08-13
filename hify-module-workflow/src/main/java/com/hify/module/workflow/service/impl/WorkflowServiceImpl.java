package com.hify.module.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.controller.dto.WorkflowCreateRequest;
import com.hify.module.workflow.controller.dto.WorkflowResponse;
import com.hify.module.workflow.controller.dto.WorkflowUpdateRequest;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.NodeConfigParser;
import com.hify.module.workflow.model.WorkflowDefinition;
import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodePosition;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.module.workflow.repository.WorkflowEdgeMapper;
import com.hify.module.workflow.repository.WorkflowMapper;
import com.hify.module.workflow.repository.WorkflowNodeMapper;
import com.hify.module.workflow.repository.entity.WorkflowEdgeEntity;
import com.hify.module.workflow.repository.entity.WorkflowEntity;
import com.hify.module.workflow.repository.entity.WorkflowNodeEntity;
import com.hify.module.workflow.service.WorkflowDefinitionParser;
import com.hify.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 工作流定义业务实现.
 *
 * <p>创建/更新时把请求里的 nodes、edges 拆写到
 * {@code hify_workflow} / {@code hify_workflow_node} / {@code hify_workflow_edge}
 * 三张表；查询详情时再从三张表组装回完整结构。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final WorkflowDefinitionParser workflowDefinitionParser;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<WorkflowResponse> page(int page, int pageSize) {
        Page<WorkflowEntity> p = new Page<>(page, pageSize);
        Page<WorkflowEntity> result = workflowMapper.selectPage(p,
                new LambdaQueryWrapper<WorkflowEntity>()
                        .orderByDesc(WorkflowEntity::getId));
        return result.convert(entity -> toResponse(entity, null, null));
    }

    @Override
    public WorkflowResponse getById(Long id) {
        WorkflowEntity entity = requireWorkflow(id);
        List<WorkflowNode> nodes = loadNodes(id);
        List<WorkflowEdge> edges = loadEdges(id);
        return toResponse(entity, nodes, edges);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse create(WorkflowCreateRequest request, Long userId) {
        validateDefinition(request.getNodes(), request.getEdges());

        WorkflowEntity entity = new WorkflowEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setStatus("DRAFT");
        entity.setVersion(1);
        entity.setCreatedBy(userId);
        workflowMapper.insert(entity);

        workflowNodeMapper.insertBatch(toNodeEntities(entity.getId(), request.getNodes()));
        workflowEdgeMapper.insertBatch(toEdgeEntities(entity.getId(), request.getEdges()));

        log.info("Workflow 创建成功: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity, request.getNodes(), request.getEdges());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse update(Long id, WorkflowUpdateRequest request) {
        WorkflowEntity entity = requireWorkflow(id);

        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if ((request.getNodes() == null) != (request.getEdges() == null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "nodes 和 edges 必须同时提供");
        }
        if (request.getNodes() != null) {
            validateDefinition(request.getNodes(), request.getEdges());
            workflowNodeMapper.delete(new LambdaQueryWrapper<WorkflowNodeEntity>()
                    .eq(WorkflowNodeEntity::getWorkflowId, id));
            workflowEdgeMapper.delete(new LambdaQueryWrapper<WorkflowEdgeEntity>()
                    .eq(WorkflowEdgeEntity::getWorkflowId, id));
            workflowNodeMapper.insertBatch(toNodeEntities(id, request.getNodes()));
            workflowEdgeMapper.insertBatch(toEdgeEntities(id, request.getEdges()));
        }
        workflowMapper.updateById(entity);

        log.info("Workflow 更新成功: id={}", id);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse publish(Long id) {
        WorkflowEntity entity = requireWorkflow(id);
        validateDefinition(loadNodes(id), loadEdges(id));
        entity.setStatus("PUBLISHED");
        entity.setVersion(entity.getVersion() + 1);
        workflowMapper.updateById(entity);
        log.info("Workflow 发布成功: id={}, version={}", id, entity.getVersion());
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowResponse disable(Long id) {
        WorkflowEntity entity = requireWorkflow(id);
        entity.setStatus("DISABLED");
        workflowMapper.updateById(entity);
        log.info("Workflow 禁用成功: id={}", id);
        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireWorkflow(id);
        workflowNodeMapper.delete(new LambdaQueryWrapper<WorkflowNodeEntity>()
                .eq(WorkflowNodeEntity::getWorkflowId, id));
        workflowEdgeMapper.delete(new LambdaQueryWrapper<WorkflowEdgeEntity>()
                .eq(WorkflowEdgeEntity::getWorkflowId, id));
        workflowMapper.deleteById(id);
        log.info("Workflow 删除成功: id={}", id);
    }

    private WorkflowEntity requireWorkflow(Long id) {
        WorkflowEntity entity = workflowMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.WORKFLOW_NOT_FOUND, "id=" + id);
        }
        return entity;
    }

    private void validateDefinition(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setNodes(nodes);
        definition.setEdges(edges);
        workflowDefinitionParser.validate(definition);
    }

    private List<WorkflowNode> loadNodes(Long workflowId) {
        List<WorkflowNodeEntity> entities = workflowNodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeEntity>()
                        .eq(WorkflowNodeEntity::getWorkflowId, workflowId)
                        .orderByAsc(WorkflowNodeEntity::getId));
        NodeConfigParser nodeConfigParser = new NodeConfigParser(objectMapper);
        return entities.stream().map(entity -> {
            WorkflowNode node = new WorkflowNode();
            node.setNodeKey(entity.getNodeKey());
            node.setName(entity.getNodeName());
            node.setType(WorkflowNodeType.valueOf(entity.getNodeType()));
            if (entity.getPositionX() != null || entity.getPositionY() != null) {
                node.setPosition(new WorkflowNodePosition(entity.getPositionX(), entity.getPositionY()));
            }
            try {
                node.setConfig(nodeConfigParser.parse(node.getType(), entity.getConfig()));
            } catch (JsonProcessingException e) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "节点配置解析失败: " + entity.getNodeKey(), e);
            }
            return node;
        }).toList();
    }

    private List<WorkflowEdge> loadEdges(Long workflowId) {
        return workflowEdgeMapper.selectList(
                        new LambdaQueryWrapper<WorkflowEdgeEntity>()
                                .eq(WorkflowEdgeEntity::getWorkflowId, workflowId)
                                .orderByAsc(WorkflowEdgeEntity::getId))
                .stream().map(entity -> {
                    WorkflowEdge edge = new WorkflowEdge();
                    edge.setId(entity.getEdgeId());
                    edge.setSourceNodeKey(entity.getSourceNodeKey());
                    edge.setCondition(entity.getEdgeCondition());
                    edge.setTargetNodeKey(entity.getTargetNodeKey());
                    return edge;
                }).toList();
    }

    private List<WorkflowNodeEntity> toNodeEntities(Long workflowId, List<WorkflowNode> nodes) {
        return nodes.stream().map(node -> {
            WorkflowNodeEntity entity = new WorkflowNodeEntity();
            entity.setWorkflowId(workflowId);
            entity.setNodeKey(node.getNodeKey());
            entity.setNodeName(node.getName());
            entity.setNodeType(node.getType().name());
            entity.setConfig(writeConfig(node.getConfig()));
            if (node.getPosition() != null) {
                entity.setPositionX(node.getPosition().getX());
                entity.setPositionY(node.getPosition().getY());
            }
            return entity;
        }).toList();
    }

    private List<WorkflowEdgeEntity> toEdgeEntities(Long workflowId, List<WorkflowEdge> edges) {
        return edges.stream().map(edge -> {
            WorkflowEdgeEntity entity = new WorkflowEdgeEntity();
            entity.setWorkflowId(workflowId);
            entity.setEdgeId(edge.getId());
            entity.setSourceNodeKey(edge.getSourceNodeKey());
            entity.setEdgeCondition(edge.getCondition());
            entity.setTargetNodeKey(edge.getTargetNodeKey());
            return entity;
        }).toList();
    }

    private String writeConfig(NodeConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION, "节点配置序列化失败", e);
        }
    }

    private WorkflowResponse toResponse(WorkflowEntity entity,
                                        List<WorkflowNode> nodes,
                                        List<WorkflowEdge> edges) {
        return WorkflowResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .nodes(nodes)
                .edges(edges)
                .build();
    }
}
