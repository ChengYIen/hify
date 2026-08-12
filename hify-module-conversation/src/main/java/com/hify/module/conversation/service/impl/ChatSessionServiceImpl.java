package com.hify.module.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.conversation.controller.dto.ChatSessionCreateRequest;
import com.hify.module.conversation.controller.dto.ChatSessionResponse;
import com.hify.module.conversation.repository.ChatSessionMapper;
import com.hify.module.conversation.repository.entity.ChatSessionEntity;
import com.hify.module.conversation.service.ChatSessionService;
import com.hify.shared.agent.AgentConfigApi;
import com.hify.shared.agent.dto.AgentConfigDTO;
import com.hify.shared.provider.ModelQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话会话业务实现.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper chatSessionMapper;
    private final AgentConfigApi agentConfigApi;
    private final ModelQueryApi modelQueryApi;

    @Override
    public IPage<ChatSessionResponse> pageByUser(Long userId, int page, int pageSize) {
        Page<ChatSessionEntity> p = new Page<>(page, pageSize);
        Page<ChatSessionEntity> result = chatSessionMapper.selectPage(p,
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getUserId, userId)
                        .orderByDesc(ChatSessionEntity::getUpdatedAt));
        IPage<ChatSessionResponse> respPage = result.convert(this::toResponse);
        fillAgentNames(respPage.getRecords());
        return respPage;
    }

    @Override
    public ChatSessionResponse getById(Long id) {
        ChatSessionEntity entity = chatSessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "id=" + id);
        }
        ChatSessionResponse response = toResponse(entity);
        response.setAgentName(resolveAgentName(entity.getAgentId()));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResponse create(ChatSessionCreateRequest request, Long userId) {
        // modelId 为空时自动解析：Agent 绑定模型 → 第一个可用模型（验收脚本可只传 agentId）
        Long modelId = request.getModelId() != null ? request.getModelId() : resolveModelId(request.getAgentId());

        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setTitle(request.getTitle());
        entity.setUserId(userId);
        entity.setAgentId(request.getAgentId());
        entity.setModelId(modelId);
        entity.setStatus("ACTIVE");
        entity.setMessageCount(0);
        entity.setTotalTokens(0);
        chatSessionMapper.insert(entity);
        log.info("ChatSession 创建成功: id={}, userId={}, modelId={}", entity.getId(), userId, modelId);
        ChatSessionResponse response = toResponse(entity);
        response.setAgentName(resolveAgentName(entity.getAgentId()));
        return response;
    }

    /**
     * 解析默认模型：Agent 绑定的 modelId 优先，其次第一个可用模型，均无则抛错.
     *
     * <p>Agent 不存在/查询失败时降级到第一个可用模型，不阻断建会话。</p>
     */
    private Long resolveModelId(Long agentId) {
        if (agentId != null) {
            try {
                AgentConfigDTO config = agentConfigApi.getAgentConfig(agentId);
                if (config != null && config.getModelId() != null) {
                    return config.getModelId();
                }
            } catch (Exception e) {
                log.warn("Agent 模型解析失败，回退到首个可用模型: agentId={}, err={}", agentId, e.getMessage());
            }
        }
        Long fallback = modelQueryApi.getFirstEnabledModelId();
        if (fallback == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "暂无可用模型，请先在模型管理中配置并启用");
        }
        return fallback;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionResponse archive(Long id) {
        ChatSessionEntity entity = chatSessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "id=" + id);
        }
        entity.setStatus("ARCHIVED");
        chatSessionMapper.updateById(entity);
        log.info("ChatSession 归档成功: id={}", id);
        ChatSessionResponse response = toResponse(entity);
        response.setAgentName(resolveAgentName(entity.getAgentId()));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ChatSessionEntity entity = chatSessionMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND, "id=" + id);
        }
        chatSessionMapper.deleteById(id);
        log.info("ChatSession 删除成功: id={}", id);
    }

    /**
     * 批量填充会话列表的 Agent 名称.
     *
     * <p>按 distinct agentId 解析，避免逐条 N+1；单个 Agent 解析失败降级为 null，
     * 不阻断整个列表返回（Agent 已删/禁用不影响历史会话展示）。</p>
     */
    private void fillAgentNames(List<ChatSessionResponse> sessions) {
        Set<Long> agentIds = sessions.stream()
                .map(ChatSessionResponse::getAgentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (agentIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        for (Long agentId : agentIds) {
            String name = resolveAgentName(agentId);
            if (name != null) {
                nameMap.put(agentId, name);
            }
        }
        for (ChatSessionResponse session : sessions) {
            if (session.getAgentId() != null) {
                session.setAgentName(nameMap.get(session.getAgentId()));
            }
        }
    }

    /**
     * 解析单个 Agent 名称；Agent 不存在/查询失败时返回 null.
     */
    private String resolveAgentName(Long agentId) {
        if (agentId == null) {
            return null;
        }
        try {
            AgentConfigDTO config = agentConfigApi.getAgentConfig(agentId);
            return config != null ? config.getName() : null;
        } catch (Exception e) {
            log.warn("解析 Agent 名称失败，降级为 null: agentId={}, err={}", agentId, e.getMessage());
            return null;
        }
    }

    private ChatSessionResponse toResponse(ChatSessionEntity entity) {
        return ChatSessionResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .userId(entity.getUserId())
                .agentId(entity.getAgentId())
                .modelId(entity.getModelId())
                .status(entity.getStatus())
                .messageCount(entity.getMessageCount())
                .totalTokens(entity.getTotalTokens())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
