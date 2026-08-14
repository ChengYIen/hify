package com.hify.module.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@link NodeExecutor} implementations by node type. Spring injects
 * all beans implementing {@link NodeExecutor} into the constructor list.
 */
@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executorMap = new HashMap<>();

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        for (NodeExecutor executor : executors) {
            NodeExecutor existing = executorMap.put(executor.nodeType(), executor);
            if (existing != null) {
                throw new IllegalStateException("Duplicate NodeExecutor for type: " + executor.nodeType());
            }
        }
    }

    public NodeExecutor get(String type) {
        NodeExecutor executor = executorMap.get(type);
        if (executor == null) {
            throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION, "未知节点类型: " + type);
        }
        return executor;
    }
}
