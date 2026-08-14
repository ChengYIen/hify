package com.hify.module.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.workflow.engine.ExecutionContext;
import com.hify.module.workflow.model.ApiCallNodeConfig;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes GET/POST API calls through the shared {@link LlmHttpClient}.
 */
@Component
@RequiredArgsConstructor
public class ApiCallNodeExecutor implements NodeExecutor {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final LlmHttpClient llmHttpClient;

    @Override
    public String nodeType() {
        return WorkflowNodeType.API_CALL.name();
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        try {
            if (!(config instanceof ApiCallNodeConfig apiConfig)) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "API_CALL 节点配置类型错误: " + node.getNodeKey());
            }
            String url = ctx.resolve(apiConfig.url());
            Map<String, String> headers = resolveHeaders(apiConfig.headers(), ctx);
            String method = apiConfig.method() == null
                    ? "GET"
                    : apiConfig.method().trim().toUpperCase();

            String body = switch (method) {
                case "GET" -> llmHttpClient.get(url, headers, READ_TIMEOUT);
                case "POST" -> llmHttpClient.post(url, headers, "");
                default -> throw new BizException(ErrorCode.PARAM_INVALID,
                        "不支持的 API_CALL method: " + method);
            };
            ctx.set(node.getNodeKey(), apiConfig.outputVariable(), body);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED,
                    "API_CALL 节点执行失败: " + node.getNodeKey(), e);
        }
    }

    private Map<String, String> resolveHeaders(Map<String, String> headers, ExecutionContext ctx) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((key, value) ->
                resolved.put(ctx.resolve(key), ctx.resolve(value)));
        return resolved;
    }
}
