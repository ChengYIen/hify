package com.hify.module.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.workflow.model.ApiCallNodeConfig;
import com.hify.module.workflow.model.ConditionNodeConfig;
import com.hify.module.workflow.model.EndNodeConfig;
import com.hify.module.workflow.model.KnowledgeNodeConfig;
import com.hify.module.workflow.model.LlmNodeConfig;
import com.hify.module.workflow.model.StartNodeConfig;
import com.hify.module.workflow.model.WorkflowDefinition;
import com.hify.module.workflow.model.WorkflowEdge;
import com.hify.module.workflow.model.WorkflowNode;
import com.hify.module.workflow.model.WorkflowNodeType;
import com.hify.module.workflow.service.WorkflowDefinitionParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Jackson-based parser. The node-level {@code type} property drives
 * deserialization of the polymorphic {@link NodeConfig} field, so callers can
 * use {@code instanceof} on the config record without touching raw JSON.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionParserImpl implements WorkflowDefinitionParser {

    private final ObjectMapper objectMapper;

    @Override
    public WorkflowDefinition parse(String json) {
        try {
            WorkflowDefinition definition = objectMapper.readValue(json, WorkflowDefinition.class);
            validate(definition);
            return definition;
        } catch (JsonProcessingException e) {
            log.warn("Workflow definition JSON parse failed: {}", e.getMessage());
            throw new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION, "工作流定义 JSON 格式错误");
        }
    }

    @Override
    public void validate(WorkflowDefinition definition) {
        if (definition == null) {
            throw invalid("定义不能为空");
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw invalid("至少需要一个节点");
        }

        Map<String, WorkflowNode> nodeMap = new HashMap<>();
        for (WorkflowNode node : definition.getNodes()) {
            if (node.getNodeKey() == null || node.getNodeKey().isBlank()) {
                throw invalid("节点 nodeKey 不能为空");
            }
            if (nodeMap.put(node.getNodeKey(), node) != null) {
                throw invalid("节点 nodeKey 重复: " + node.getNodeKey());
            }
            if (node.getType() == null) {
                throw invalid("节点 type 不能为空: " + node.getNodeKey());
            }
            if (node.getConfig() == null) {
                throw invalid("节点配置不能为空: " + node.getNodeKey());
            }
            if (!configMatches(node)) {
                throw invalid("节点配置与类型不匹配: " + node.getNodeKey());
            }
        }

        Set<String> usedConditions = new HashSet<>();
        for (WorkflowEdge edge : definition.getEdges()) {
            if (edge.getSourceNodeKey() == null || edge.getTargetNodeKey() == null) {
                throw invalid("边的 sourceNodeKey/targetNodeKey 不能为空");
            }
            if (!nodeMap.containsKey(edge.getSourceNodeKey())) {
                throw invalid("边引用了不存在的源节点: " + edge.getSourceNodeKey());
            }
            if (!nodeMap.containsKey(edge.getTargetNodeKey())) {
                throw invalid("边引用了不存在的目标节点: " + edge.getTargetNodeKey());
            }

            String condition = edge.getCondition() == null ? "default" : edge.getCondition();
            if (!usedConditions.add(edge.getSourceNodeKey() + ":" + condition)) {
                throw invalid("同一节点同一 condition 只能有一条边: "
                        + edge.getSourceNodeKey() + " -> " + condition);
            }
        }
    }

    private boolean configMatches(WorkflowNode node) {
        return switch (node.getType()) {
            case START -> node.getConfig() instanceof StartNodeConfig;
            case END -> node.getConfig() instanceof EndNodeConfig;
            case LLM -> node.getConfig() instanceof LlmNodeConfig;
            case CONDITION -> node.getConfig() instanceof ConditionNodeConfig;
            case API_CALL -> node.getConfig() instanceof ApiCallNodeConfig;
            case KNOWLEDGE -> node.getConfig() instanceof KnowledgeNodeConfig;
        };
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.WORKFLOW_INVALID_DEFINITION, message);
    }
}
