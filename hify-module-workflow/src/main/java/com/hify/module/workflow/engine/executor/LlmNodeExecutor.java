package com.hify.module.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.engine.ExecutionContext;
import com.hify.module.workflow.model.LlmNodeConfig;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Synchronous LLM node. Model resolution and provider adapter routing are
 * delegated to {@link LlmProviderApi}, which already encapsulates the model
 * config, provider, and adapter factory lookup.
 */
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final LlmProviderApi llmProviderApi;

    @Override
    public String nodeType() {
        return WorkflowNodeType.LLM.name();
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        try {
            if (!(config instanceof LlmNodeConfig llmConfig)) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "LLM 节点配置类型错误: " + node.getNodeKey());
            }
            String prompt = ctx.resolve(llmConfig.prompt());

            LlmResponseDTO response = llmProviderApi.chat(LlmRequestDTO.builder()
                    .modelId(llmConfig.modelConfigId())
                    .messages(List.of(LlmRequestDTO.Message.builder()
                            .role("user")
                            .content(prompt)
                            .build()))
                    .stream(false)
                    .build());

            ctx.set(node.getNodeKey(), llmConfig.outputVariable(), response.getContent());
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED,
                    "LLM 节点执行失败: " + node.getNodeKey(), e);
        }
    }
}
