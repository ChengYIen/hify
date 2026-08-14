package com.hify.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.repository.AgentMapper;
import com.hify.module.agent.repository.AgentToolMapper;
import com.hify.module.agent.repository.entity.AgentToolEntity;
import com.hify.module.agent.service.AgentToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 工具关联查询业务实现.
 */
@Service
@RequiredArgsConstructor
public class AgentToolServiceImpl implements AgentToolService {

    private final AgentToolMapper agentToolMapper;
    private final AgentMapper agentMapper;

    @Override
    public List<AgentToolResponse> listByAgentId(Long agentId) {
        requireAgent(agentId);
        return agentToolMapper.selectList(
                        new LambdaQueryWrapper<AgentToolEntity>()
                                .eq(AgentToolEntity::getAgentId, agentId)
                                .orderByAsc(AgentToolEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AgentToolResponse getById(Long id) {
        AgentToolEntity entity = agentToolMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_TOOL_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    private void requireAgent(Long agentId) {
        if (agentMapper.selectById(agentId) == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + agentId);
        }
    }

    private AgentToolResponse toResponse(AgentToolEntity entity) {
        return AgentToolResponse.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .toolId(entity.getToolId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
