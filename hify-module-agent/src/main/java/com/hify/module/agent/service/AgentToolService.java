package com.hify.module.agent.service;

import com.hify.module.agent.controller.dto.AgentToolResponse;

import java.util.List;

/**
 * Agent 工具关联查询业务接口.
 */
public interface AgentToolService {

    List<AgentToolResponse> listByAgentId(Long agentId);

    AgentToolResponse getById(Long id);
}
