package com.hify.module.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.module.workflow.engine.ExecutionContext;
import com.hify.module.workflow.model.ApiCallNodeConfig;
import com.hify.module.workflow.model.ConditionNodeConfig;
import com.hify.module.workflow.model.KnowledgeNodeConfig;
import com.hify.module.workflow.model.LlmNodeConfig;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.shared.llm.LlmProviderApi;
import com.hify.shared.llm.dto.LlmRequestDTO;
import com.hify.shared.llm.dto.LlmResponseDTO;
import com.hify.shared.rag.RagRetrievalApi;
import com.hify.shared.rag.dto.RagChunkDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeExecutorsTest {

    @Mock
    private LlmProviderApi llmProviderApi;

    @Mock
    private LlmHttpClient llmHttpClient;

    @Mock
    private RagRetrievalApi ragRetrievalApi;

    @InjectMocks
    private LlmNodeExecutor llmNodeExecutor;

    @InjectMocks
    private ConditionNodeExecutor conditionNodeExecutor;

    @InjectMocks
    private ApiCallNodeExecutor apiCallNodeExecutor;

    @InjectMocks
    private KnowledgeNodeExecutor knowledgeNodeExecutor;

    @Test
    void llm_should_resolve_prompt_and_write_content() {
        ExecutionContext ctx = new ExecutionContext(1L, "7天内能退货吗");
        WorkflowNode node = node("llm1", WorkflowNodeType.LLM,
                new LlmNodeConfig(5L, "请回答：{{start.userMessage}}", "answer"));
        when(llmProviderApi.chat(any(LlmRequestDTO.class)))
                .thenReturn(LlmResponseDTO.builder().content("可以退货").build());

        llmNodeExecutor.execute(node, node.getConfig(), ctx);

        ArgumentCaptor<LlmRequestDTO> captor = ArgumentCaptor.forClass(LlmRequestDTO.class);
        verify(llmProviderApi).chat(captor.capture());
        assertThat(captor.getValue().getModelId()).isEqualTo(5L);
        assertThat(captor.getValue().getMessages().get(0).getContent())
                .isEqualTo("请回答：7天内能退货吗");
        assertThat(ctx.get("llm1", "answer")).isEqualTo("可以退货");
    }

    @Test
    void condition_should_support_equals_not_equals_and_literals() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");
        ctx.set("classify", "intent", "售后");

        executeCondition(ctx, "{{classify.intent}} == \"售后\"", "matched");
        assertThat(ctx.get("router", "matched")).isEqualTo(true);

        executeCondition(ctx, "{{classify.intent}} != \"售后\"", "notMatched");
        assertThat(ctx.get("router", "notMatched")).isEqualTo(false);

        executeCondition(ctx, "true", "literal");
        assertThat(ctx.get("router", "literal")).isEqualTo(true);
    }

    @Test
    void condition_should_return_resolved_string_for_plain_expression() {
        ExecutionContext ctx = new ExecutionContext(1L, "hello");
        ctx.set("classify", "intent", "售后");

        executeCondition(ctx, "{{classify.intent}}", "route");

        assertThat(ctx.get("router", "route")).isEqualTo("售后");
    }

    @Test
    void condition_should_support_contains_operator() {
        ExecutionContext ctx = new ExecutionContext(1L, "耳机坏了怎么申请保修");

        executeCondition(ctx, "{{start.userMessage}} contains \"保修\"", "match");

        assertThat(ctx.get("router", "match")).isEqualTo(true);
    }

    @Test
    void api_call_should_resolve_url_and_headers_for_get() {
        ExecutionContext ctx = new ExecutionContext(1L, "abc");
        WorkflowNode node = node("api1", WorkflowNodeType.API_CALL,
                new ApiCallNodeConfig("https://x/{{start.userMessage}}", "GET",
                        Map.of("X-Token", "{{start.userMessage}}"), "response"));
        when(llmHttpClient.get(anyString(), anyMap(), any(Duration.class)))
                .thenReturn("{\"ok\":true}");

        apiCallNodeExecutor.execute(node, node.getConfig(), ctx);

        verify(llmHttpClient).get(eq("https://x/abc"), eq(Map.of("X-Token", "abc")),
                any(Duration.class));
        assertThat(ctx.get("api1", "response")).isEqualTo("{\"ok\":true}");
    }

    @Test
    void knowledge_should_search_and_join_chunk_content() {
        ExecutionContext ctx = new ExecutionContext(1L, "7天内能退货吗");
        WorkflowNode node = node("kb1", WorkflowNodeType.KNOWLEDGE,
                new KnowledgeNodeConfig(3L, "查询：{{start.userMessage}}", 2, "docs"));
        when(ragRetrievalApi.search(3L, "查询：7天内能退货吗", 2))
                .thenReturn(List.of(chunk("条款一"), chunk("条款二")));

        knowledgeNodeExecutor.execute(node, node.getConfig(), ctx);

        assertThat(ctx.get("kb1", "docs")).isEqualTo("条款一\n条款二");
    }

    @Test
    void registry_should_dispatch_by_type_and_reject_unknown_type() {
        NodeExecutor llm = mock(NodeExecutor.class);
        NodeExecutor condition = mock(NodeExecutor.class);
        when(llm.nodeType()).thenReturn("LLM");
        when(condition.nodeType()).thenReturn("CONDITION");
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(llm, condition));

        assertThat(registry.get("LLM")).isSameAs(llm);
        assertThat(registry.get("CONDITION")).isSameAs(condition);
        Throwable thrown = catchThrowable(() -> registry.get("BANANA"));
        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_INVALID_DEFINITION);
    }

    private void executeCondition(ExecutionContext ctx, String expression, String outputVariable) {
        WorkflowNode node = node("router", WorkflowNodeType.CONDITION,
                new ConditionNodeConfig(expression, outputVariable));
        conditionNodeExecutor.execute(node, node.getConfig(), ctx);
    }

    private WorkflowNode node(String nodeKey, WorkflowNodeType type, NodeConfig config) {
        WorkflowNode node = new WorkflowNode();
        node.setNodeKey(nodeKey);
        node.setType(type);
        node.setConfig(config);
        return node;
    }

    private RagChunkDTO chunk(String content) {
        return RagChunkDTO.builder().content(content).build();
    }
}
