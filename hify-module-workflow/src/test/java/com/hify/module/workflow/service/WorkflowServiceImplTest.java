package com.hify.module.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.module.workflow.controller.dto.WorkflowCreateRequest;
import com.hify.module.workflow.controller.dto.WorkflowResponse;
import com.hify.module.workflow.controller.dto.WorkflowUpdateRequest;
import com.hify.module.workflow.model.ConditionNodeConfig;
import com.hify.module.workflow.model.LlmNodeConfig;
import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.module.workflow.repository.WorkflowEdgeMapper;
import com.hify.module.workflow.repository.WorkflowMapper;
import com.hify.module.workflow.repository.WorkflowNodeMapper;
import com.hify.module.workflow.repository.entity.WorkflowEdgeEntity;
import com.hify.module.workflow.repository.entity.WorkflowEntity;
import com.hify.module.workflow.repository.entity.WorkflowNodeEntity;
import com.hify.module.workflow.service.impl.WorkflowServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

    @Mock
    private WorkflowMapper workflowMapper;

    @Mock
    private WorkflowNodeMapper workflowNodeMapper;

    @Mock
    private WorkflowEdgeMapper workflowEdgeMapper;

    @Mock
    private WorkflowDefinitionParser workflowDefinitionParser;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    @Test
    void should_create_workflow_and_split_nodes_edges_into_three_tables() {
        doAnswer(invocation -> {
            WorkflowEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(workflowMapper).insert(any(WorkflowEntity.class));

        WorkflowCreateRequest request = new WorkflowCreateRequest();
        request.setName("智能客服分类工作流");
        request.setDescription("真实配置验证");
        request.setNodes(sampleNodes());
        request.setEdges(sampleEdges());

        WorkflowResponse response = workflowService.create(request, 42L);

        verify(workflowMapper).insert(any(WorkflowEntity.class));
        verify(workflowNodeMapper).insertBatch(anyList());
        verify(workflowEdgeMapper).insertBatch(anyList());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNodes()).hasSize(5);
        assertThat(response.getEdges()).hasSize(4);
    }

    @Test
    void should_get_detail_and_restore_typed_config_from_three_tables() {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setId(1L);
        entity.setName("智能客服分类工作流");
        entity.setStatus("DRAFT");
        entity.setVersion(1);
        when(workflowMapper.selectById(1L)).thenReturn(entity);

        WorkflowNodeEntity classify = nodeEntity("classify", "LLM",
                "{\"prompt\":\"判断问题类型，返回：售前/售后/技术支持\",\"outputVariable\":\"intent\"}");
        WorkflowNodeEntity router = nodeEntity("router", "CONDITION",
                "{\"expression\":\"{{intent}}\",\"outputVariable\":\"route\"}");
        WorkflowNodeEntity aftersale = nodeEntity("aftersale", "LLM",
                "{\"prompt\":\"你是售后客服，回答退换货和保修问题\",\"outputVariable\":\"answer\"}");
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of(classify, router, aftersale));

        WorkflowEdgeEntity edge1 = edgeEntity("classify", null, "router");
        WorkflowEdgeEntity edge2 = edgeEntity("router", "售后", "aftersale");
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of(edge1, edge2));

        WorkflowResponse response = workflowService.getById(1L);

        assertThat(response.getNodes()).hasSize(3);
        assertThat(response.getNodes().get(0).getNodeKey()).isEqualTo("classify");
        assertThat(response.getNodes().get(0).getType()).isEqualTo(WorkflowNodeType.LLM);
        LlmNodeConfig classifyConfig = (LlmNodeConfig) response.getNodes().get(0).getConfig();
        assertThat(classifyConfig.prompt()).contains("售前/售后/技术支持");
        assertThat(classifyConfig.outputVariable()).isEqualTo("intent");

        assertThat(response.getNodes().get(1).getType()).isEqualTo(WorkflowNodeType.CONDITION);
        ConditionNodeConfig routerConfig = (ConditionNodeConfig) response.getNodes().get(1).getConfig();
        assertThat(routerConfig.expression()).isEqualTo("{{intent}}");

        assertThat(response.getEdges()).hasSize(2);
        assertThat(response.getEdges().get(0).getSourceNodeKey()).isEqualTo("classify");
        assertThat(response.getEdges().get(0).getCondition()).isNull();
        assertThat(response.getEdges().get(1).getSourceNodeKey()).isEqualTo("router");
        assertThat(response.getEdges().get(1).getTargetNodeKey()).isEqualTo("aftersale");
        assertThat(response.getEdges().get(1).getCondition()).isEqualTo("售后");
    }

    @Test
    void should_replace_nodes_and_edges_on_update() {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setId(1L);
        entity.setName("旧工作流");
        entity.setStatus("DRAFT");
        entity.setVersion(1);
        when(workflowMapper.selectById(1L)).thenReturn(entity);
        when(workflowNodeMapper.selectList(any())).thenReturn(List.of());
        when(workflowEdgeMapper.selectList(any())).thenReturn(List.of());

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        request.setName("新工作流");
        request.setNodes(sampleNodes());
        request.setEdges(sampleEdges());

        workflowService.update(1L, request);

        InOrder inOrder = inOrder(workflowNodeMapper, workflowEdgeMapper);
        inOrder.verify(workflowNodeMapper).delete(any());
        inOrder.verify(workflowEdgeMapper).delete(any());
        inOrder.verify(workflowNodeMapper).insertBatch(anyList());
        inOrder.verify(workflowEdgeMapper).insertBatch(anyList());
    }

    @Test
    void should_logically_delete_workflow_with_nodes_and_edges() {
        WorkflowEntity entity = new WorkflowEntity();
        entity.setId(1L);
        when(workflowMapper.selectById(1L)).thenReturn(entity);

        workflowService.delete(1L);

        verify(workflowNodeMapper).delete(any());
        verify(workflowEdgeMapper).delete(any());
        verify(workflowMapper).deleteById(1L);
    }

    private List<WorkflowNode> sampleNodes() {
        WorkflowNode classify = new WorkflowNode();
        classify.setNodeKey("classify");
        classify.setName("问题分类");
        classify.setType(WorkflowNodeType.LLM);
        classify.setConfig(new LlmNodeConfig(null, "判断问题类型，返回：售前/售后/技术支持", "intent"));

        WorkflowNode router = new WorkflowNode();
        router.setNodeKey("router");
        router.setName("路由分发");
        router.setType(WorkflowNodeType.CONDITION);
        router.setConfig(new ConditionNodeConfig("{{intent}}", "route"));

        WorkflowNode presale = new WorkflowNode();
        presale.setNodeKey("presale");
        presale.setName("售前咨询");
        presale.setType(WorkflowNodeType.LLM);
        presale.setConfig(new LlmNodeConfig(null, "你是产品顾问，介绍产品功能和优势", "answer"));

        WorkflowNode aftersale = new WorkflowNode();
        aftersale.setNodeKey("aftersale");
        aftersale.setName("售后服务");
        aftersale.setType(WorkflowNodeType.LLM);
        aftersale.setConfig(new LlmNodeConfig(null, "你是售后客服，回答退换货和保修问题", "answer"));

        WorkflowNode techsupport = new WorkflowNode();
        techsupport.setNodeKey("techsupport");
        techsupport.setName("技术支持");
        techsupport.setType(WorkflowNodeType.LLM);
        techsupport.setConfig(new LlmNodeConfig(null, "你是技术工程师，帮用户排查使用问题", "answer"));

        return List.of(classify, router, presale, aftersale, techsupport);
    }

    private List<WorkflowEdge> sampleEdges() {
        return List.of(
                edge("classify", null, "router"),
                edge("router", "售前", "presale"),
                edge("router", "售后", "aftersale"),
                edge("router", "技术支持", "techsupport"));
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

    private WorkflowEdgeEntity edgeEntity(String source, String condition, String target) {
        WorkflowEdgeEntity entity = new WorkflowEdgeEntity();
        entity.setWorkflowId(1L);
        entity.setSourceNodeKey(source);
        entity.setEdgeCondition(condition);
        entity.setTargetNodeKey(target);
        return entity;
    }

    private WorkflowEdge edge(String source, String condition, String target) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setSourceNodeKey(source);
        edge.setCondition(condition);
        edge.setTargetNodeKey(target);
        return edge;
    }
}
