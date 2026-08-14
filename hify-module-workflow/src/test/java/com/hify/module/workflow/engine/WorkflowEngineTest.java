package com.hify.module.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.engine.executor.NodeExecutor;
import com.hify.module.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.module.workflow.model.NodeConfig;
import com.hify.module.workflow.model.NodeConfigParser;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.repository.WorkflowEdgeMapper;
import com.hify.module.workflow.repository.WorkflowNodeMapper;
import com.hify.module.workflow.repository.WorkflowNodeRunMapper;
import com.hify.module.workflow.repository.WorkflowRunMapper;
import com.hify.module.workflow.repository.entity.WorkflowEdgeEntity;
import com.hify.module.workflow.repository.entity.WorkflowNodeEntity;
import com.hify.module.workflow.repository.entity.WorkflowNodeRunEntity;
import com.hify.module.workflow.repository.entity.WorkflowRunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineTest {

    @Mock
    private WorkflowNodeMapper workflowNodeMapper;

    @Mock
    private WorkflowEdgeMapper workflowEdgeMapper;

    @Mock
    private NodeExecutorRegistry nodeExecutorRegistry;

    @Mock
    private WorkflowRunMapper workflowRunMapper;

    @Mock
    private WorkflowNodeRunMapper workflowNodeRunMapper;

    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        engine = new WorkflowEngine(
                workflowNodeMapper,
                workflowEdgeMapper,
                new NodeConfigParser(objectMapper),
                nodeExecutorRegistry,
                workflowRunMapper,
                workflowNodeRunMapper,
                objectMapper);
    }

    @Test
    void should_execute_linear_workflow_and_return_end_output() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("start", "START", "{}"),
                nodeEntity("llm1", "LLM",
                        "{\"modelConfigId\":5,\"prompt\":\"{{start.userMessage}}\",\"outputVariable\":\"answer\"}"),
                nodeEntity("end", "END", "{\"outputVariable\":\"llm1.answer\"}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(
                edgeEntity("start", "llm1", null),
                edgeEntity("llm1", "end", null)));
        mockRunIds();

        NodeExecutor llmExecutor = mockLlmExecutor("售后处理结果");
        when(nodeExecutorRegistry.get("LLM")).thenReturn(llmExecutor);

        WorkflowRunEntity result = engine.execute(1L, "7天内能退货吗");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOutput()).isEqualTo("\"售后处理结果\"");
        ArgumentCaptor<WorkflowNodeRunEntity> nodeRunCaptor =
                ArgumentCaptor.forClass(WorkflowNodeRunEntity.class);
        verify(workflowNodeRunMapper).updateById(nodeRunCaptor.capture());
        assertThat(nodeRunCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(nodeRunCaptor.getValue().getNodeKey()).isEqualTo("llm1");
    }

    @Test
    void should_route_condition_true_branch_and_run_only_that_branch() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("start", "START", "{}"),
                nodeEntity("condition", "CONDITION",
                        "{\"expression\":\"{{classify.intent}}\",\"outputVariable\":\"route\"}"),
                nodeEntity("llm_true", "LLM",
                        "{\"modelConfigId\":5,\"prompt\":\"true\",\"outputVariable\":\"answer\"}"),
                nodeEntity("llm_false", "LLM",
                        "{\"modelConfigId\":5,\"prompt\":\"false\",\"outputVariable\":\"answer\"}"),
                nodeEntity("end", "END", "{\"outputVariable\":\"llm_true.answer\"}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(
                edgeEntity("start", "condition", null),
                edgeEntity("condition", "llm_true", "true"),
                edgeEntity("condition", "llm_false", "false"),
                edgeEntity("llm_true", "end", null),
                edgeEntity("llm_false", "end", null)));
        mockRunIds();

        NodeExecutor conditionExecutor = org.mockito.Mockito.mock(NodeExecutor.class);
        doAnswer(invocation -> {
            WorkflowNode node = invocation.getArgument(0);
            ExecutionContext ctx = invocation.getArgument(2);
            ctx.set("classify", "intent", "售后");
            ctx.set(node.getNodeKey(), "route", true);
            return null;
        }).when(conditionExecutor).execute(any(WorkflowNode.class), any(NodeConfig.class),
                any(ExecutionContext.class));
        when(nodeExecutorRegistry.get("CONDITION")).thenReturn(conditionExecutor);

        NodeExecutor llmExecutor = mockLlmExecutor(null);
        when(nodeExecutorRegistry.get("LLM")).thenReturn(llmExecutor);

        WorkflowRunEntity result = engine.execute(1L, "7天内能退货吗");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOutput()).isEqualTo("\"llm_true answer\"");
        ArgumentCaptor<WorkflowNode> nodeCaptor = ArgumentCaptor.forClass(WorkflowNode.class);
        verify(llmExecutor, org.mockito.Mockito.times(1))
                .execute(nodeCaptor.capture(), any(NodeConfig.class), any(ExecutionContext.class));
        assertThat(nodeCaptor.getValue().getNodeKey()).isEqualTo("llm_true");
    }

    @Test
    void should_mark_node_and_run_failed_when_executor_throws() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("start", "START", "{}"),
                nodeEntity("llm1", "LLM",
                        "{\"modelConfigId\":5,\"prompt\":\"boom\",\"outputVariable\":\"answer\"}"),
                nodeEntity("end", "END", "{\"outputVariable\":\"llm1.answer\"}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(
                edgeEntity("start", "llm1", null),
                edgeEntity("llm1", "end", null)));
        mockRunIds();

        NodeExecutor failingExecutor = org.mockito.Mockito.mock(NodeExecutor.class);
        doThrow(new BizException(ErrorCode.WORKFLOW_EXECUTION_FAILED, "boom"))
                .when(failingExecutor).execute(any(WorkflowNode.class), any(NodeConfig.class),
                        any(ExecutionContext.class));
        when(nodeExecutorRegistry.get("LLM")).thenReturn(failingExecutor);

        Throwable thrown = catchThrowable(() -> engine.execute(1L, "7天内能退货吗"));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getMessage()).contains("boom");

        ArgumentCaptor<WorkflowNodeRunEntity> nodeRunCaptor =
                ArgumentCaptor.forClass(WorkflowNodeRunEntity.class);
        verify(workflowNodeRunMapper).updateById(nodeRunCaptor.capture());
        assertThat(nodeRunCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(nodeRunCaptor.getValue().getError()).contains("boom");

        ArgumentCaptor<WorkflowRunEntity> runCaptor =
                ArgumentCaptor.forClass(WorkflowRunEntity.class);
        verify(workflowRunMapper).updateById(runCaptor.capture());
        assertThat(runCaptor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void should_reject_workflow_without_start_node() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("llm1", "LLM",
                        "{\"modelConfigId\":5,\"prompt\":\"x\",\"outputVariable\":\"answer\"}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of());

        Throwable thrown = catchThrowable(() -> engine.execute(1L, "7天内能退货吗"));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getMessage()).contains("START");
    }

    @Test
    void should_reject_missing_target_node() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("start", "START", "{}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(
                edgeEntity("start", "missing", null)));

        Throwable thrown = catchThrowable(() -> engine.execute(1L, "7天内能退货吗"));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getMessage()).contains("目标节点不存在");
    }

    @Test
    void should_stop_after_max_execution_steps() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("start", "START", "{}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(
                edgeEntity("start", "start", null)));

        Throwable thrown = catchThrowable(() -> engine.execute(1L, "7天内能退货吗"));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getMessage()).contains("执行步数超过");
    }

    @Test
    void should_continue_execution_when_run_persistence_fails() {
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(
                nodeEntity("start", "START", "{}"),
                nodeEntity("llm1", "LLM",
                        "{\"modelConfigId\":5,\"prompt\":\"x\",\"outputVariable\":\"answer\"}"),
                nodeEntity("end", "END", "{\"outputVariable\":\"llm1.answer\"}")));
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(
                edgeEntity("start", "llm1", null),
                edgeEntity("llm1", "end", null)));
        doThrow(new RuntimeException("db down")).when(workflowRunMapper)
                .insert(any(WorkflowRunEntity.class));
        doThrow(new RuntimeException("db down")).when(workflowNodeRunMapper)
                .insert(any(WorkflowNodeRunEntity.class));

        NodeExecutor llmExecutor = mockLlmExecutor("still works");
        when(nodeExecutorRegistry.get("LLM")).thenReturn(llmExecutor);

        WorkflowRunEntity result = engine.execute(1L, "7天内能退货吗");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOutput()).isEqualTo("\"still works\"");
    }

    private void mockRunIds() {
        doAnswer(invocation -> {
            WorkflowRunEntity run = invocation.getArgument(0);
            run.setId(1L);
            return 1;
        }).when(workflowRunMapper).insert(any(WorkflowRunEntity.class));
        doAnswer(invocation -> {
            WorkflowNodeRunEntity nodeRun = invocation.getArgument(0);
            nodeRun.setId(10L);
            return 1;
        }).when(workflowNodeRunMapper).insert(any(WorkflowNodeRunEntity.class));
    }

    private NodeExecutor mockLlmExecutor(String answer) {
        NodeExecutor executor = org.mockito.Mockito.mock(NodeExecutor.class);
        doAnswer(invocation -> {
            WorkflowNode node = invocation.getArgument(0);
            ExecutionContext ctx = invocation.getArgument(2);
            ctx.set(node.getNodeKey(), "answer",
                    answer != null ? answer : node.getNodeKey() + " answer");
            return null;
        }).when(executor).execute(any(WorkflowNode.class), any(NodeConfig.class),
                any(ExecutionContext.class));
        return executor;
    }

    private WorkflowNodeEntity nodeEntity(String nodeKey, String nodeType, String config) {
        WorkflowNodeEntity entity = new WorkflowNodeEntity();
        entity.setWorkflowId(1L);
        entity.setNodeKey(nodeKey);
        entity.setNodeName(nodeKey);
        entity.setNodeType(nodeType);
        entity.setConfig(config);
        return entity;
    }

    private WorkflowEdgeEntity edgeEntity(String source, String target, String condition) {
        WorkflowEdgeEntity entity = new WorkflowEdgeEntity();
        entity.setWorkflowId(1L);
        entity.setSourceNodeKey(source);
        entity.setTargetNodeKey(target);
        entity.setEdgeCondition(condition);
        return entity;
    }
}
