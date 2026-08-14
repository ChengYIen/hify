package com.hify.module.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.engine.ExecutionContext;
import com.hify.module.workflow.model.ConditionNodeConfig;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import org.springframework.stereotype.Component;

/**
 * Evaluates a resolved condition expression. Supports {@code ==}, {@code !=},
 * and the literals {@code true} / {@code false}.
 */
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public String nodeType() {
        return WorkflowNodeType.CONDITION.name();
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        try {
            if (!(config instanceof ConditionNodeConfig conditionConfig)) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "CONDITION 节点配置类型错误: " + node.getNodeKey());
            }
            String resolved = ctx.resolve(conditionConfig.expression());
            Object result = evaluate(resolved);
            ctx.set(node.getNodeKey(), conditionConfig.outputVariable(), result);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED,
                    "CONDITION 节点执行失败: " + node.getNodeKey(), e);
        }
    }

    private Object evaluate(String expression) {
        String expr = expression == null ? "" : expression.trim();
        int notEqualIndex = expr.indexOf("!=");
        if (notEqualIndex >= 0) {
            return !normalize(expr.substring(0, notEqualIndex))
                    .equals(normalize(expr.substring(notEqualIndex + 2)));
        }
        int equalIndex = expr.indexOf("==");
        if (equalIndex >= 0) {
            return normalize(expr.substring(0, equalIndex))
                    .equals(normalize(expr.substring(equalIndex + 2)));
        }
        String containsToken = " contains ";
        int containsIndex = expr.toLowerCase().indexOf(containsToken);
        if (containsIndex >= 0) {
            String left = normalize(expr.substring(0, containsIndex));
            String right = normalize(expr.substring(containsIndex + containsToken.length()));
            return left.contains(right);
        }
        if ("true".equalsIgnoreCase(expr)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(expr)) {
            return Boolean.FALSE;
        }
        return expr.isBlank() ? Boolean.FALSE : expr;
    }

    private String normalize(String value) {
        String v = value == null ? "" : value.trim();
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
