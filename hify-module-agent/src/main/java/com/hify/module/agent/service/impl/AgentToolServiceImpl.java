package com.hify.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.agent.controller.dto.AgentToolRequest;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.repository.AgentMapper;
import com.hify.module.agent.repository.AgentToolMapper;
import com.hify.module.agent.repository.entity.AgentToolEntity;
import com.hify.module.agent.service.AgentToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 工具关联业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolServiceImpl implements AgentToolService {

    private final AgentToolMapper agentToolMapper;
    private final AgentMapper agentMapper;

    @Override
    public List<AgentToolResponse> listByAgentId(Long agentId) {
        if (agentMapper.selectById(agentId) == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + agentId);
        }
        List<AgentToolEntity> list = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, agentId)
                        .orderByAsc(AgentToolEntity::getPriority));
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public AgentToolResponse getById(Long id) {
        AgentToolEntity entity = agentToolMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_TOOL_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentToolResponse create(Long agentId, AgentToolRequest request) {
        if (agentMapper.selectById(agentId) == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + agentId);
        }
        AgentToolEntity entity = new AgentToolEntity();
        entity.setAgentId(agentId);
        entity.setToolName(request.getToolName());
        entity.setToolType(request.getToolType());
        entity.setToolConfig(request.getToolConfig());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        agentToolMapper.insert(entity);
        log.info("AgentTool 创建成功: id={}, toolName={}", entity.getId(), entity.getToolName());
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentToolResponse update(Long id, AgentToolRequest request) {
        AgentToolEntity entity = agentToolMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_TOOL_NOT_FOUND, "id=" + id);
        }
        if (request.getToolName() != null) {
            entity.setToolName(request.getToolName());
        }
        if (request.getToolType() != null) {
            entity.setToolType(request.getToolType());
        }
        if (request.getToolConfig() != null) {
            entity.setToolConfig(request.getToolConfig());
        }
        if (request.getPriority() != null) {
            entity.setPriority(request.getPriority());
        }
        agentToolMapper.updateById(entity);
        log.info("AgentTool 更新成功: id={}", id);
        return toResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AgentToolEntity entity = agentToolMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_TOOL_NOT_FOUND, "id=" + id);
        }
        agentToolMapper.deleteById(id);
        log.info("AgentTool 删除成功: id={}", id);
    }

    private AgentToolResponse toResponse(AgentToolEntity entity) {
        return AgentToolResponse.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .toolName(entity.getToolName())
                .toolType(entity.getToolType())
                .toolConfig(entity.getToolConfig())
                .priority(entity.getPriority())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
