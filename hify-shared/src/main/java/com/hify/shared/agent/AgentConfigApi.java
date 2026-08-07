package com.hify.shared.agent;

import com.hify.shared.agent.dto.AgentConfigDTO;

/**
 * Agent 配置查询接口（跨模块共享）.
 * <p>
 * conversation 模块通过此接口查询 Agent 配置，
 * 由 agent 模块实现。
 * </p>
 */
public interface AgentConfigApi {

    /**
     * 按 ID 查询 Agent 配置.
     *
     * @param agentId Agent ID
     * @return Agent 配置，不存在抛 BizException
     */
    AgentConfigDTO getAgentConfig(Long agentId);
}
