package com.hify.module.agent.service.impl;

import com.hify.module.agent.controller.dto.AgentResponse;
import com.hify.module.agent.service.AgentService;
import com.hify.shared.agent.AgentConfigApi;
import com.hify.shared.agent.dto.AgentConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Agent 配置查询接口实现（供 conversation 等模块跨模块调用）.
 *
 * <p>conversation 模块通过 {@link AgentConfigApi} 获取 Agent 的 systemPrompt / 模型 /
 * 温度 / 上下文轮次等运行时配置，本实现桥接 agent 模块的 {@link AgentService}。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentConfigApiImpl implements AgentConfigApi {

    private final AgentService agentService;

    @Override
    public AgentConfigDTO getAgentConfig(Long agentId) {
        // 不存在时 AgentService.getById 已抛 BizException(AGENT_NOT_FOUND)
        AgentResponse response = agentService.getById(agentId);
        return AgentConfigDTO.builder()
                .id(response.getId())
                .name(response.getName())
                .systemPrompt(response.getSystemPrompt())
                .modelId(response.getModelConfigId())
                .workflowId(response.getWorkflowId())
                .temperature(response.getTemperature() == null
                        ? null
                        : response.getTemperature().doubleValue())
                .maxTokens(response.getMaxTokens())
                .maxIterations(response.getMaxContextTurns())
                .toolsEnabled(response.getToolsEnabled() != null && response.getToolsEnabled() == 1)
                .knowledgeIds(response.getKnowledgeIds())
                .build();
    }
}
