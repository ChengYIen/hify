package com.hify.module.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.engine.ExecutionContext;
import com.hify.module.workflow.model.KnowledgeNodeConfig;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.shared.rag.RagRetrievalApi;
import com.hify.shared.rag.dto.RagChunkDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Knowledge retrieval node backed by the shared {@link RagRetrievalApi}.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeNodeExecutor implements NodeExecutor {

    private static final int DEFAULT_TOP_K = 5;

    private final RagRetrievalApi ragRetrievalApi;

    @Override
    public String nodeType() {
        return WorkflowNodeType.KNOWLEDGE.name();
    }

    @Override
    public void execute(WorkflowNode node, NodeConfig config, ExecutionContext ctx) {
        try {
            if (!(config instanceof KnowledgeNodeConfig knowledgeConfig)) {
                throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION,
                        "KNOWLEDGE 节点配置类型错误: " + node.getNodeKey());
            }
            String query = ctx.resolve(knowledgeConfig.query());
            int topK = knowledgeConfig.topK() != null ? knowledgeConfig.topK() : DEFAULT_TOP_K;

            List<RagChunkDTO> chunks = ragRetrievalApi.search(
                    knowledgeConfig.knowledgeBaseId(), query, topK);
            String content = chunks.stream()
                    .map(RagChunkDTO::getContent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));

            ctx.set(node.getNodeKey(), knowledgeConfig.outputVariable(), content);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED,
                    "KNOWLEDGE 节点执行失败: " + node.getNodeKey(), e);
        }
    }
}
