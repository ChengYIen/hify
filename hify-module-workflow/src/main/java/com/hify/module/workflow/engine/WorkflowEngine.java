package com.hify.module.workflow.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.module.workflow.model.ConditionNodeConfig;
import com.hify.module.workflow.model.EndNodeConfig;
import com.hify.module.workflow.model.NodeConfigParser;
import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.module.workflow.repository.WorkflowEdgeMapper;
import com.hify.module.workflow.repository.WorkflowNodeMapper;
import com.hify.module.workflow.repository.WorkflowNodeRunMapper;
import com.hify.module.workflow.repository.WorkflowRunMapper;
import com.hify.module.workflow.repository.entity.WorkflowEdgeEntity;
import com.hify.module.workflow.repository.entity.WorkflowNodeEntity;
import com.hify.module.workflow.repository.entity.WorkflowNodeRunEntity;
import com.hify.module.workflow.repository.entity.WorkflowRunEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous workflow engine. Step 1 supports linear execution over
 * unconditional edges only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEngine {

    private static final int MAX_EXECUTION_STEPS = 50;

    private final WorkflowNodeMapper workflowNodeMapper;
    private final WorkflowEdgeMapper workflowEdgeMapper;
    private final NodeConfigParser nodeConfigParser;
    private final NodeExecutorRegistry nodeExecutorRegistry;
    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowNodeRunMapper workflowNodeRunMapper;
    private final ObjectMapper objectMapper;

    public WorkflowRunEntity execute(Long workflowId, String userMessage) {
        Map<String, WorkflowNode> nodeMap = loadNodeMap(workflowId);
        Map<String, List<WorkflowEdge>> edgeMap = loadEdgeMap(workflowId);

        WorkflowNode start = nodeMap.values().stream()
                .filter(node -> node.getType() == WorkflowNodeType.START)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "未找到 START 节点: workflowId=" + workflowId));

        WorkflowRunEntity run = createRun(workflowId, userMessage);
        ExecutionContext ctx = new ExecutionContext(run.getId(), userMessage);

        try {
            String currentKey = start.getNodeKey();
            Object output = null;
            int steps = 0;
            while (currentKey != null) {
                steps++;
                if (steps > MAX_EXECUTION_STEPS) {
                    throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                            "执行步数超过 " + MAX_EXECUTION_STEPS + "，终止执行: workflowId=" + workflowId);
                }
                WorkflowNode node = nodeMap.get(currentKey);
                if (node == null) {
                    throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                            "目标节点不存在: " + currentKey);
                }
                if (node.getType() == WorkflowNodeType.END) {
                    output = resolveEndOutput(node, ctx);
                    break;
                }
                if (node.getType() == WorkflowNodeType.START) {
                    currentKey = findNext(node, edgeMap, ctx);
                    continue;
                }

                WorkflowNodeRunEntity nodeRun = createNodeRun(run.getId(), node);
                LocalDateTime startedAt = LocalDateTime.now();
                try {
                    nodeExecutorRegistry.get(node.getType().name())
                            .execute(node, node.getConfig(), ctx);
                } catch (Exception e) {
                    failNodeRun(nodeRun, startedAt, e);
                    throw e;
                }
                completeNodeRun(nodeRun, ctx, startedAt);

                currentKey = findNext(node, edgeMap, ctx);
            }

            completeRun(run, output);
            return run;
        } catch (Exception e) {
            failRun(run, e);
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED,
                    "工作流执行失败: workflowId=" + workflowId, e);
        }
    }

    private String findNext(WorkflowNode node, Map<String, List<WorkflowEdge>> edgeMap,
                            ExecutionContext ctx) {
        List<WorkflowEdge> edges = edgeMap.getOrDefault(node.getNodeKey(), List.of());
        if (edges.isEmpty()) {
            return null;
        }
        if (node.getType() == WorkflowNodeType.CONDITION) {
            String branch = conditionBranch(node, ctx);
            return edges.stream()
                    .filter(edge -> branch != null && branch.equals(edge.getCondition()))
                    .findFirst()
                    .map(WorkflowEdge::getTargetNodeKey)
                    .orElse(null);
        }
        return edges.stream()
                .filter(edge -> edge.getCondition() == null)
                .findFirst()
                .orElse(edges.get(0))
                .getTargetNodeKey();
    }

    private String conditionBranch(WorkflowNode node, ExecutionContext ctx) {
        if (node.getConfig() instanceof ConditionNodeConfig config) {
            Object value = ctx.get(node.getNodeKey(), config.outputVariable());
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }

    private Object resolveEndOutput(WorkflowNode endNode, ExecutionContext ctx) {
        EndNodeConfig config = (EndNodeConfig) endNode.getConfig();
        if (config.outputVariable() != null) {
            return ctx.snapshot().get(config.outputVariable());
        }
        return ctx.resolve(config.outputTemplate());
    }

    private Map<String, WorkflowNode> loadNodeMap(Long workflowId) {
        List<WorkflowNodeEntity> entities = workflowNodeMapper.selectList(
                new LambdaQueryWrapper<WorkflowNodeEntity>()
                        .eq(WorkflowNodeEntity::getWorkflowId, workflowId));
        Map<String, WorkflowNode> nodeMap = new HashMap<>();
        for (WorkflowNodeEntity entity : entities) {
            WorkflowNode node = new WorkflowNode();
            node.setNodeKey(entity.getNodeKey());
            node.setName(entity.getNodeName());
            node.setType(WorkflowNodeType.valueOf(entity.getNodeType()));
            try {
                node.setConfig(nodeConfigParser.parse(node.getType(), entity.getConfig()));
            } catch (JsonProcessingException e) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "节点配置解析失败: " + entity.getNodeKey(), e);
            }
            nodeMap.put(node.getNodeKey(), node);
        }
        return nodeMap;
    }

    private Map<String, List<WorkflowEdge>> loadEdgeMap(Long workflowId) {
        List<WorkflowEdgeEntity> entities = workflowEdgeMapper.selectList(
                new LambdaQueryWrapper<WorkflowEdgeEntity>()
                        .eq(WorkflowEdgeEntity::getWorkflowId, workflowId));
        Map<String, List<WorkflowEdge>> edgeMap = new HashMap<>();
        for (WorkflowEdgeEntity entity : entities) {
            WorkflowEdge edge = new WorkflowEdge();
            edge.setSourceNodeKey(entity.getSourceNodeKey());
            edge.setTargetNodeKey(entity.getTargetNodeKey());
            edge.setCondition(entity.getEdgeCondition());
            edgeMap.computeIfAbsent(edge.getSourceNodeKey(), key -> new ArrayList<>()).add(edge);
        }
        return edgeMap;
    }

    private WorkflowRunEntity createRun(Long workflowId, String userMessage) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setWorkflowId(workflowId);
        run.setStatus("RUNNING");
        run.setInput(userMessage);
        run.setCreatedAt(LocalDateTime.now());
        try {
            workflowRunMapper.insert(run);
        } catch (Exception e) {
            log.warn("WorkflowRun 落库失败，继续执行: workflowId={}, err={}",
                    workflowId, e.getMessage());
        }
        return run;
    }

    private WorkflowNodeRunEntity createNodeRun(Long workflowRunId, WorkflowNode node) {
        WorkflowNodeRunEntity nodeRun = new WorkflowNodeRunEntity();
        nodeRun.setWorkflowRunId(workflowRunId);
        nodeRun.setNodeKey(node.getNodeKey());
        nodeRun.setNodeType(node.getType().name());
        nodeRun.setStatus("RUNNING");
        nodeRun.setCreatedAt(LocalDateTime.now());
        try {
            workflowNodeRunMapper.insert(nodeRun);
        } catch (Exception e) {
            log.warn("WorkflowNodeRun 落库失败，继续执行: nodeKey={}, err={}",
                    node.getNodeKey(), e.getMessage());
        }
        return nodeRun;
    }

    private void completeNodeRun(WorkflowNodeRunEntity nodeRun, ExecutionContext ctx,
                                 LocalDateTime startedAt) {
        LocalDateTime now = LocalDateTime.now();
        nodeRun.setStatus("SUCCESS");
        nodeRun.setOutputs(toJson(ctx.snapshot()));
        nodeRun.setElapsedMs((int) Duration.between(startedAt, now).toMillis());
        nodeRun.setFinishedAt(now);
        try {
            workflowNodeRunMapper.updateById(nodeRun);
        } catch (Exception e) {
            log.warn("WorkflowNodeRun 更新失败: nodeKey={}, err={}",
                    nodeRun.getNodeKey(), e.getMessage());
        }
    }

    private void failNodeRun(WorkflowNodeRunEntity nodeRun, LocalDateTime startedAt, Exception e) {
        LocalDateTime now = LocalDateTime.now();
        nodeRun.setStatus("FAILED");
        nodeRun.setError(truncate(e.getMessage(), 500));
        nodeRun.setElapsedMs((int) Duration.between(startedAt, now).toMillis());
        nodeRun.setFinishedAt(now);
        try {
            workflowNodeRunMapper.updateById(nodeRun);
        } catch (Exception ex) {
            log.warn("WorkflowNodeRun 失败状态更新失败: nodeKey={}, err={}",
                    nodeRun.getNodeKey(), ex.getMessage());
        }
    }

    private void completeRun(WorkflowRunEntity run, Object output) {
        LocalDateTime now = LocalDateTime.now();
        run.setStatus("SUCCESS");
        run.setOutput(toJson(output));
        run.setFinishedAt(now);
        run.setElapsedMs((int) Duration.between(run.getCreatedAt(), now).toMillis());
        try {
            workflowRunMapper.updateById(run);
        } catch (Exception e) {
            log.warn("WorkflowRun 完成状态更新失败: runId={}, err={}",
                    run.getId(), e.getMessage());
        }
    }

    private void failRun(WorkflowRunEntity run, Exception e) {
        LocalDateTime now = LocalDateTime.now();
        run.setStatus("FAILED");
        run.setError(truncate(e.getMessage(), 500));
        run.setFinishedAt(now);
        if (run.getCreatedAt() != null) {
            run.setElapsedMs((int) Duration.between(run.getCreatedAt(), now).toMillis());
        }
        try {
            workflowRunMapper.updateById(run);
        } catch (Exception ex) {
            log.warn("WorkflowRun 失败状态更新失败: runId={}, err={}",
                    run.getId(), ex.getMessage());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("执行记录输出序列化失败，回退为字符串: {}", e.getMessage());
            return String.valueOf(value);
        }
    }

    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        return message.length() <= maxLength
                ? message
                : message.substring(0, maxLength);
    }
}
