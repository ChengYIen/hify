package com.hify.module.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.config.CacheNames;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.agent.controller.dto.AgentCreateRequest;
import com.hify.module.agent.controller.dto.AgentDetailResponse;
import com.hify.module.agent.controller.dto.AgentListResponse;
import com.hify.module.agent.controller.dto.AgentResponse;
import com.hify.module.agent.controller.dto.AgentToolRequest;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.controller.dto.AgentUpdateRequest;
import com.hify.common.util.RedisUtil;
import com.hify.module.agent.repository.AgentMapper;
import com.hify.module.agent.repository.AgentToolMapper;
import com.hify.module.agent.repository.ToolDefinitionMapper;
import com.hify.module.agent.repository.entity.AgentEntity;
import com.hify.module.agent.repository.entity.AgentToolEntity;
import com.hify.module.agent.repository.entity.ToolDefinitionEntity;
import com.hify.module.agent.service.AgentService;
import com.hify.shared.conversation.SessionQueryApi;
import com.hify.shared.provider.ModelQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agent 配置业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper agentMapper;
    private final AgentToolMapper agentToolMapper;
    private final ToolDefinitionMapper toolDefinitionMapper;
    private final SessionQueryApi sessionQueryApi;
    private final ModelQueryApi modelQueryApi;

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
        // 缓存命中直接返回
        AgentResponse cached = getCached(id);
        if (cached != null) {
            return cached;
        }
        AgentResponse response = getById(id);
        List<AgentToolEntity> tools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id)
                        .orderByAsc(AgentToolEntity::getPriority));
        response.setTools(tools.stream().map(this::toToolResponse).collect(Collectors.toList()));
        response.setToolCount(tools.size());
        // 写入缓存
        putCache(id, response);
        return response;
    }

    // =====================================================================
    // 创建 Agent（四步流程）
    // =====================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.AGENT, key = "'list'")
    public AgentDetailResponse create(AgentCreateRequest request) {
        // ---- 第一步：检查 name 唯一性 ----
        Long nameCount = agentMapper.selectCount(
                new LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getName, request.getName()));
        if (nameCount > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "Agent 名称已存在: " + request.getName());
        }

        // ---- 第二步：跨模块校验 modelConfigId（调 shared 接口，不直接查 mapper） ----
        if (!modelQueryApi.isModelAvailable(request.getModelConfigId())) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "模型不可用或不存在: modelConfigId=" + request.getModelConfigId());
        }

        // ---- 第三步：事务中 INSERT agent + 批量 INSERT agent_tool ----
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

        // 处理 toolIds：查 ToolDefinition → 创建 agent_tool 绑定记录
        List<AgentToolEntity> savedTools = Collections.emptyList();
        if (request.getToolIds() != null && !request.getToolIds().isEmpty()) {
            savedTools = bindToolsByDefinitionIds(entity.getId(), request.getToolIds());
        }

        // ---- 第四步：@CacheEvict 清除 agent 列表缓存（注解已声明） + 清除详情缓存 ----
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
        // 改名时校验唯一性
        if (request.getName() != null && !request.getName().equals(entity.getName())) {
            Long nameCount = agentMapper.selectCount(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getName, request.getName()));
            if (nameCount > 0) {
                throw new BizException(ErrorCode.DUPLICATE, "Agent 名称已存在: " + request.getName());
            }
        }
        // 改模型时校验可用性
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
        if (request.getWorkflowId() != null) {
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

        // 查询现有工具用于返回
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
    public List<AgentToolRequest> updateTools(Long id, List<AgentToolRequest> tools) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + id);
        }
        // 逻辑删除旧工具（先查 ID → deleteByIds，确保走 @TableLogic）
        List<AgentToolEntity> oldTools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id));
        if (!oldTools.isEmpty()) {
            List<Long> oldIds = oldTools.stream()
                    .map(AgentToolEntity::getId)
                    .collect(Collectors.toList());
            agentToolMapper.deleteByIds(oldIds);
        }
        // 插入新工具
        if (tools != null && !tools.isEmpty()) {
            batchInsertTools(id, tools);
        }
        log.info("Agent 工具更新成功: agentId={}, toolCount={}",
                id, tools != null ? tools.size() : 0);
        evictCache(id);
        return tools != null ? tools : Collections.emptyList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheNames.AGENT, key = "'list'")
    public void delete(Long id) {
        AgentEntity entity = agentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.AGENT_NOT_FOUND, "id=" + id);
        }
        // 检查是否有活跃会话引用，有则不允许删除
        long activeCount = sessionQueryApi.countActiveByAgentId(id);
        if (activeCount > 0) {
            throw new BizException(ErrorCode.DATA_CONFLICT,
                    "该 Agent 下有 " + activeCount + " 个活跃对话，请先结束对话后再删除");
        }
        // 逻辑删除关联的工具（通过 @TableLogic，转为 UPDATE deleted=1）
        List<AgentToolEntity> tools = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolEntity>()
                        .eq(AgentToolEntity::getAgentId, id));
        if (!tools.isEmpty()) {
            List<Long> toolIds = tools.stream()
                    .map(AgentToolEntity::getId)
                    .collect(Collectors.toList());
            agentToolMapper.deleteByIds(toolIds);
        }
        // 逻辑删除 Agent（通过 @TableLogic）
        agentMapper.deleteById(id);
        evictCache(id);
        log.info("Agent 删除成功: id={}, 级联删除工具数={}", id, tools.size());
    }

    // =====================================================================
    // 私有转换方法
    // =====================================================================

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

    /**
     * Entity → AgentDetailResponse（含工具列表）.
     */
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

    /**
     * Entity 转 DTO 并附带工具列表.
     */
    private AgentResponse toResponseWithTools(AgentEntity entity, List<AgentToolEntity> tools) {
        AgentResponse response = toResponse(entity);
        response.setToolCount(tools.size());
        response.setTools(tools.stream()
                .map(this::toToolResponse)
                .collect(Collectors.toList()));
        return response;
    }

    /**
     * 根据工具定义 ID 列表创建 agent_tool 绑定记录.
     * <p>
     * 逐一查询 {@link ToolDefinitionEntity}，校验存在且启用，
     * 将其信息复制到 {@link AgentToolEntity} 中。
     * </p>
     *
     * @param agentId Agent ID
     * @param toolDefIds 工具定义 ID 列表
     * @return 创建成功的工具关联实体列表
     */
    private List<AgentToolEntity> bindToolsByDefinitionIds(Long agentId, List<Long> toolDefIds) {
        List<AgentToolEntity> result = new ArrayList<>(toolDefIds.size());
        for (int i = 0; i < toolDefIds.size(); i++) {
            Long toolDefId = toolDefIds.get(i);
            ToolDefinitionEntity def = toolDefinitionMapper.selectById(toolDefId);
            if (def == null) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "工具定义不存在: toolDefId=" + toolDefId);
            }
            if (!"ENABLED".equals(def.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "工具定义未启用: toolName=" + def.getToolName());
            }
            AgentToolEntity toolEntity = new AgentToolEntity();
            toolEntity.setAgentId(agentId);
            toolEntity.setToolName(def.getToolName());
            toolEntity.setToolType(def.getToolType());
            toolEntity.setToolConfig(def.getToolConfig());
            toolEntity.setPriority(i);
            agentToolMapper.insert(toolEntity);
            result.add(toolEntity);
        }
        return result;
    }

    /**
     * 批量插入工具关联并返回持久化后的实体列表.
     */
    private List<AgentToolEntity> batchInsertTools(Long agentId, List<AgentToolRequest> tools) {
        List<AgentToolEntity> result = new ArrayList<>(tools.size());
        for (int i = 0; i < tools.size(); i++) {
            AgentToolRequest tool = tools.get(i);
            AgentToolEntity toolEntity = new AgentToolEntity();
            toolEntity.setAgentId(agentId);
            toolEntity.setToolName(tool.getToolName());
            toolEntity.setToolType(tool.getToolType());
            toolEntity.setToolConfig(tool.getToolConfig());
            toolEntity.setPriority(tool.getPriority() != null ? tool.getPriority() : i);
            agentToolMapper.insert(toolEntity);
            result.add(toolEntity);
        }
        return result;
    }

    private AgentToolResponse toToolResponse(AgentToolEntity entity) {
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

    // ----------------------------------------------------
    // Redis 缓存（Redis 不可用时自动降级为直接查库）
    // ----------------------------------------------------

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
