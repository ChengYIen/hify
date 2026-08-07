package com.hify.module.conversation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.module.conversation.repository.ChatSessionMapper;
import com.hify.module.conversation.repository.entity.ChatSessionEntity;
import com.hify.shared.conversation.SessionQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 会话查询接口实现（供 Agent 等模块跨模块调用）.
 */
@Service
@RequiredArgsConstructor
public class SessionQueryApiImpl implements SessionQueryApi {

    private final ChatSessionMapper chatSessionMapper;

    @Override
    public long countActiveByAgentId(Long agentId) {
        return chatSessionMapper.selectCount(
                new LambdaQueryWrapper<ChatSessionEntity>()
                        .eq(ChatSessionEntity::getAgentId, agentId)
                        .eq(ChatSessionEntity::getStatus, "ACTIVE"));
    }
}
