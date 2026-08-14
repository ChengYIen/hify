package com.hify.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.config.CacheNames;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.RedisUtil;
import com.hify.module.agent.controller.dto.AgentCreateRequest;
import com.hify.module.agent.controller.dto.AgentDetailResponse;
import com.hify.module.agent.controller.dto.AgentListResponse;
import com.hify.module.agent.controller.dto.AgentResponse;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.controller.dto.AgentUpdateRequest;
import com.hify.module.agent.repository.AgentMapper;
import com.hify.module.agent.repository.AgentToolMapper;
import com.hify.module.agent.repository.entity.AgentEntity;
import com.hify.module.agent.repository.entity.AgentToolEntity;
import com.hify.module.agent.service.AgentService;
import com.hify.shared.conversation.SessionQueryApi;
import com.hify.shared.provider.ModelQueryApi;
import com.hify.shared.tool.McpToolQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agent 配置业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private static final int MAX_AGENT_TOOLS = 10;

    private final AgentMapper agentMapper;
    private final AgentToolMapper agentToolMapper;
    private final SessionQueryApi sessionQueryApi;
    private final ModelQueryApi modelQueryApi;
    private final McpToolQueryApi mcpToolQueryApi;

    /** Redis 工具（可选注入，Redis 不可用时自动降级为直接查库） */
    @Autowired(required = false)
    private RedisUtil redisUtil;

    private static final String CACHE_KEY_PREFIX = "hify:agent:detail:";
    private static final long CACHE_TTL_MINUTES = 10;

    @Override
    @Cacheable(cacheNames = CacheNames.AGENT, key = "'list'")
    public IPage<AgentListResponse> page(int page, int pageSize) {
        Page<AgentEntity> p = new Page<>(page, pageSize);
        Page<AgentEntity> result = agentMapper.selectPage(p,
                new LambdaQueryWrapper<AgentEntity>()
                        .orderByDesc(AgentEntity::getId));
        return result.convert(entity -> {
            AgentListResponse response = toListResponse(entity);
            Long count = agentToolMapper.selectCount(
                    new LambdaQueryWrapper<AgentToolEntity>()
                            .eq(AgentToolEntity::getAgentId, entity.getId()));
            response.setToolCount(count.intValue());
            return response;
        });
    }

    @Override
    public AgentResponse getById(Long id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + id);
        }
        return toResponse(entity);
    }

    @Override
    public AgentResponse getByIdWithTools(Long id) {
        AgentResponse cached = getCached(id);
        if (cached != null) {
            return cached;
        }
        AgentResponse response = getById(id);
        List<AgentToolEntity> tools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id)
                        .orderByAsc(AgentToolEntity::getId));
        response.setTools(tools.stream().map(this::toToolResponse).collect(Collectors.toList()));
        response.setToolCount(tools.size());
        putCache(id, response);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.AGENT, key = "'list'")
    public AgentDetailResponse create(AgentCreateRequest request) {
        Long nameCount = agentMapper.selectCount(
                new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getName, request.getName()));
        if (nameCount > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "Agent 名称已存在: " + request.getName());
        }

        if (!modelQueryApi.isModelAvailable(request.getModelConfigId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "模型不可用或不存在: modelConfigId=" + request.getModelConfigId());
        }

        AgentEntity entity = new AgentEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setAvatarUrl(request.getAvatarUrl());
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setModelId(request.getModelConfigId());
        entity.setWorkflowId(request.getWorkflowId());
        entity.setTemperature(request.getTemperature());
        entity.setMaxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        entity.setMaxIterations(request.getMaxContextTurns() != null ? request.getMaxContextTurns() : 10);
        entity.setToolsEnabled(request.getToolsEnabled() != null ? request.getToolsEnabled() : 0);
        entity.setKnowledgeIds(request.getKnowledgeIds());
        entity.setStatus(request.getStatus() != null ? request.getStatus() : "DRAFT");
        agentMapper.insert(entity);

        List<Long> toolIds = validateToolIds(request.getToolIds());
        List<AgentToolEntity> savedTools = bindToolIds(entity.getId(), toolIds);

        evictCache(entity.getId());
        log.info("Agent 创建成功: id={}, name={}, toolCount={}",
                entity.getId(), entity.getName(), savedTools.size());
        return toDetailResponse(entity, savedTools);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.AGENT, key = "'list'")
    public AgentResponse update(Long id, AgentUpdateRequest request) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + id);
        }
        if (request.getName() != null && !request.getName().equals(entity.getName())) {
            Long nameCount = agentMapper.selectCount(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getName, request.getName()));
            if (nameCount > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "Agent 名称已存在: " + request.getName());
            }
        }
        if (request.getModelId() != null && !request.getModelId().equals(entity.getModelId())) {
            if (!modelQueryApi.isModelAvailable(request.getModelId())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "模型不可用或不存在: modelId=" + request.getModelId());
            }
        }
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getAvatarUrl() != null) {
            entity.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getSystemPrompt() != null) {
            entity.setSystemPrompt(request.getSystemPrompt());
        }
        if (request.getModelId() != null) {
            entity.setModelId(request.getModelId());
        }
        if (Boolean.TRUE.equals(request.getUnbindWorkflow())) {
            entity.setWorkflowId(null);
        } else if (request.getWorkflowId() != null) {
            entity.setWorkflowId(request.getWorkflowId());
        }
        if (request.getTemperature() != null) {
            entity.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            entity.setMaxTokens(request.getMaxTokens());
        }
        if (request.getMaxIterations() != null) {
            entity.setMaxIterations(request.getMaxIterations());
        }
        if (request.getToolsEnabled() != null) {
            entity.setToolsEnabled(request.getToolsEnabled());
        }
        if (request.getKnowledgeIds() != null) {
            entity.setKnowledgeIds(request.getKnowledgeIds());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        agentMapper.updateById(entity);

        List<AgentToolEntity> tools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id));
        log.info("Agent 更新成功: id={}, toolCount={}", id, tools.size());
        evictCache(id);
        return toResponseWithTools(entity, tools);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.AGENT, key = "'list'")
    public List<AgentToolResponse> updateTools(Long id, List<Long> toolIds) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + id);
        }

        List<Long> validToolIds = validateToolIds(toolIds);

        List<AgentToolEntity> oldTools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id));
        if (!oldTools.isEmpty()) {
            List<Long> oldIds = oldTools.stream()
                    .map(AgentToolEntity::getId)
                    .collect(Collectors.toList());
            agentToolMapper.deleteByIds(oldIds);
        }

        List<AgentToolEntity> savedTools = bindToolIds(id, validToolIds);
        log.info("Agent 工具更新成功: agentId={}, toolCount={}", id, savedTools.size());
        evictCache(id);
        return savedTools.stream()
                .map(this::toToolResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.AGENT, key = "'list'")
    public void delete(Long id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + id);
        }

        long activeCount = sessionQueryApi.countActiveByAgentId(id);
        if (activeCount > 0) {
            throw new BizException(ErrorCode.DATA_CONFLICT,
                    "该 Agent 下有 " + activeCount + " 个活跃对话，请先结束对话后再删除");
        }

        List<AgentToolEntity> tools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id));
        if (!tools.isEmpty()) {
            List<Long> toolIds = tools.stream()
                    .map(AgentToolEntity::getId)
                    .collect(Collectors.toList());
            agentToolMapper.deleteByIds(toolIds);
        }
        agentMapper.deleteById(id);
        evictCache(id);
        log.info("Agent 删除成功: id={}, 级联删除工具数={}", id, tools.size());
    }

    private List<Long> validateToolIds(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinctToolIds = toolIds.stream()
                .distinct()
                .collect(Collectors.toList());
        if (distinctToolIds.size() > MAX_AGENT_TOOLS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "一个 Agent 最多绑定 " + MAX_AGENT_TOOLS + " 个工具");
        }

        List<Long> availableToolIds = mcpToolQueryApi.listAvailableToolIds(distinctToolIds);
        Set<Long> availableSet = new HashSet<>(availableToolIds);
        for (Long toolId : distinctToolIds) {
            if (!availableSet.contains(toolId)) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "工具不存在或其 MCP Server 未启用: toolId=" + toolId);
            }
        }
        return distinctToolIds;
    }

    private List<AgentToolEntity> bindToolIds(Long agentId, List<Long> toolIds) {
        List<AgentToolEntity> result = new ArrayList<>(toolIds.size());
        for (Long toolId : toolIds) {
            AgentToolEntity toolEntity = new AgentToolEntity();
            toolEntity.setAgentId(agentId);
            toolEntity.setToolId(toolId);
            agentToolMapper.insert(toolEntity);
            result.add(toolEntity);
        }
        return result;
    }

    private AgentListResponse toListResponse(AgentEntity entity) {
        return AgentListResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .avatarUrl(entity.getAvatarUrl())
                .systemPrompt(entity.getSystemPrompt())
                .modelConfigId(entity.getModelId())
                .workflowId(entity.getWorkflowId())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .maxContextTurns(entity.getMaxIterations())
                .toolsEnabled(entity.getToolsEnabled())
                .knowledgeIds(entity.getKnowledgeIds())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentResponse toResponse(AgentEntity entity) {
        return AgentResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .avatarUrl(entity.getAvatarUrl())
                .systemPrompt(entity.getSystemPrompt())
                .modelConfigId(entity.getModelId())
                .workflowId(entity.getWorkflowId())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .maxContextTurns(entity.getMaxIterations())
                .toolsEnabled(entity.getToolsEnabled())
                .knowledgeIds(entity.getKnowledgeIds())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentDetailResponse toDetailResponse(AgentEntity entity, List<AgentToolEntity> tools) {
        return AgentDetailResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .avatarUrl(entity.getAvatarUrl())
                .systemPrompt(entity.getSystemPrompt())
                .modelConfigId(entity.getModelId())
                .workflowId(entity.getWorkflowId())
                .temperature(entity.getTemperature())
                .maxTokens(entity.getMaxTokens())
                .maxContextTurns(entity.getMaxIterations())
                .toolsEnabled(entity.getToolsEnabled())
                .knowledgeIds(entity.getKnowledgeIds())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .toolCount(tools.size())
                .tools(tools.stream()
                        .map(this::toToolResponse)
                        .collect(Collectors.toList()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentResponse toResponseWithTools(AgentEntity entity, List<AgentToolEntity> tools) {
        AgentResponse response = toResponse(entity);
        response.setToolCount(tools.size());
        response.setTools(tools.stream()
                .map(this::toToolResponse)
                .collect(Collectors.toList()));
        return response;
    }

    private AgentToolResponse toToolResponse(AgentToolEntity entity) {
        return AgentToolResponse.builder()
                .id(entity.getId())
                .agentId(entity.getAgentId())
                .toolId(entity.getToolId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String cacheKey(Long agentId) {
        return CACHE_KEY_PREFIX + agentId;
    }

    private AgentResponse getCached(Long agentId) {
        if (redisUtil == null) {
            return null;
        }
        try {
            return redisUtil.get(cacheKey(agentId));
        } catch (Exception e) {
            log.warn("Redis 读取缓存失败, agentId={}, 降级为直接查库", agentId, e);
            return null;
        }
    }

    private void putCache(Long agentId, AgentResponse response) {
        if (redisUtil == null) {
            return;
        }
        try {
            redisUtil.set(cacheKey(agentId), response, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 写缓存失败, agentId={}, 不影响业务", agentId, e);
        }
    }

    private void evictCache(Long agentId) {
        if (redisUtil == null) {
            return;
        }
        try {
            redisUtil.delete(cacheKey(agentId));
        } catch (Exception e) {
            log.warn("Redis 逐出缓存失败, agentId={}, 不影响业务", agentId, e);
        }
    }
}
