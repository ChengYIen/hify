package com.hify.module.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.model.ConditionNodeConfig;
import com.hify.module.workflow.model.LlmNodeConfig;
import com.hify.module.workflow.model.WorkflowDefinition;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.module.workflow.service.impl.WorkflowDefinitionParserImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class WorkflowDefinitionParserTest {

    private final WorkflowDefinitionParser parser =
            new WorkflowDefinitionParserImpl(new ObjectMapper());

    @Test
    void should_parse_real_customer_service_workflow() {
        WorkflowDefinition definition = parser.parse(validDefinitionJson());

        assertThat(definition.getNodes()).hasSize(5);
        assertThat(definition.getEdges()).hasSize(4);

        WorkflowNode classify = definition.getNodes().get(0);
        assertThat(classify.getNodeKey()).isEqualTo("classify");
        assertThat(classify.getType()).isEqualTo(WorkflowNodeType.LLM);
        LlmNodeConfig classifyConfig = (LlmNodeConfig) classify.getConfig();
        assertThat(classifyConfig.prompt()).contains("售前/售后/技术支持");
        assertThat(classifyConfig.outputVariable()).isEqualTo("intent");

        WorkflowNode router = definition.getNodes().get(1);
        assertThat(router.getNodeKey()).isEqualTo("router");
        assertThat(router.getType()).isEqualTo(WorkflowNodeType.CONDITION);
        ConditionNodeConfig routerConfig = (ConditionNodeConfig) router.getConfig();
        assertThat(routerConfig.expression()).isEqualTo("{{intent}}");
        assertThat(routerConfig.outputVariable()).isEqualTo("route");

        assertThat(definition.getEdges().get(0).getSourceNodeKey()).isEqualTo("classify");
        assertThat(definition.getEdges().get(0).getTargetNodeKey()).isEqualTo("router");
        assertThat(definition.getEdges().get(0).getCondition()).isNull();

        assertThat(definition.getEdges().get(2).getSourceNodeKey()).isEqualTo("router");
        assertThat(definition.getEdges().get(2).getTargetNodeKey()).isEqualTo("aftersale");
        assertThat(definition.getEdges().get(2).getCondition()).isEqualTo("售后");
    }

    @Test
    void should_accept_workflow_without_start_and_end_nodes() {
        WorkflowDefinition definition = parser.parse(validDefinitionJson());

        assertThat(definition.getNodes())
                .extracting(WorkflowNode::getType)
                .doesNotContain(WorkflowNodeType.START, WorkflowNodeType.END);
    }

    @Test
    void should_reject_edge_pointing_to_missing_node() {
        String json = validDefinitionJson().replace(
                "\"targetNodeKey\": \"presale\"",
                "\"targetNodeKey\": \"missing\"");

        Throwable thrown = catchThrowable(() -> parser.parse(json));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_INVALID_DEFINITION);
        assertThat(thrown).hasMessageContaining("missing");
    }

    @Test
    void should_reject_unknown_node_type() {
        String json = validDefinitionJson().replace(
                "\"type\": \"LLM\"",
                "\"type\": \"BANANA\"");

        Throwable thrown = catchThrowable(() -> parser.parse(json));

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.WORKFLOW_INVALID_DEFINITION);
    }

    @Test
    void should_serialize_back_with_same_field_names() throws Exception {
        WorkflowDefinition definition = parser.parse(validDefinitionJson());

        String json = new ObjectMapper().writeValueAsString(definition);

        assertThat(json).contains("\"nodeKey\":\"classify\"");
        assertThat(json).contains("\"sourceNodeKey\":\"router\"");
        assertThat(json).contains("\"condition\":\"售后\"");
        assertThat(json).contains("\"outputVariable\":\"intent\"");
    }

    private String validDefinitionJson() {
        return """
                {
                  "schemaVersion": "1.0",
                  "nodes": [
                    {
                      "nodeKey": "classify",
                      "type": "LLM",
                      "name": "问题分类",
                      "config": {
                        "prompt": "判断问题类型，返回：售前/售后/技术支持",
                        "outputVariable": "intent"
                      }
                    },
                    {
                      "nodeKey": "router",
                      "type": "CONDITION",
                      "name": "路由分发",
                      "config": {
                        "expression": "{{intent}}",
                        "outputVariable": "route"
                      }
                    },
                    {
                      "nodeKey": "presale",
                      "type": "LLM",
                      "name": "售前咨询",
                      "config": {
                        "prompt": "你是产品顾问，介绍产品功能和优势",
                        "outputVariable": "answer"
                      }
                    },
                    {
                      "nodeKey": "aftersale",
                      "type": "LLM",
                      "name": "售后服务",
                      "config": {
                        "prompt": "你是售后客服，回答退换货和保修问题",
                        "outputVariable": "answer"
                      }
                    },
                    {
                      "nodeKey": "techsupport",
                      "type": "LLM",
                      "name": "技术支持",
                      "config": {
                        "prompt": "你是技术工程师，帮用户排查使用问题",
                        "outputVariable": "answer"
                      }
                    }
                  ],
                  "edges": [
                    { "sourceNodeKey": "classify", "targetNodeKey": "router", "condition": null },
                    { "sourceNodeKey": "router", "targetNodeKey": "presale", "condition": "售前" },
                    { "sourceNodeKey": "router", "targetNodeKey": "aftersale", "condition": "售后" },
                    { "sourceNodeKey": "router", "targetNodeKey": "techsupport", "condition": "技术支持" }
                  ]
                }
                """;
    }
}
