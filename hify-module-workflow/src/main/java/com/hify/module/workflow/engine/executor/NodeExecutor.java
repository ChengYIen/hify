package com.hify.module.workflow.engine.executor;

import com.hify.module.workflow.engine.ExecutionContext;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.WorkflowNode;

/**
 * Executes one workflow node. Implementations must stay stateless and write
 * all run state through {@link ExecutionContext}.
 */
public interface NodeExecutor {

    void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx);

    String nodeType();
}
