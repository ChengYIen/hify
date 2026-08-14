package com.hify.module.agent.service.impl;

import com.hify.common.exception.BizException;
import com.hify.module.agent.controller.dto.AgentCreateRequest;
import com.hify.module.agent.controller.dto.AgentDetailResponse;
import com.hify.module.agent.controller.dto.AgentToolResponse;
import com.hify.module.agent.repository.AgentMapper;
import com.hify.module.agent.repository.AgentToolMapper;
import com.hify.module.agent.repository.entity.AgentEntity;
import com.hify.module.agent.repository.entity.AgentToolEntity;
import com.hify.shared.conversation.SessionQueryApi;
import com.hify.shared.provider.ModelQueryApi;
import com.hify.shared.tool.McpToolQueryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 工具绑定业务单测.
 */
class AgentServiceImplTest {

    private AgentMapper agentMapper;
    private AgentToolMapper agentToolMapper;
    private SessionQueryApi sessionQueryApi;
    private ModelQueryApi modelQueryApi;
    private McpToolQueryApi mcpToolQueryApi;
    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        agentMapper = mock(AgentMapper.class);
        agentToolMapper = mock(AgentToolMapper.class);
        sessionQueryApi = mock(SessionQueryApi.class);
        modelQueryApi = mock(ModelQueryApi.class);
        mcpToolQueryApi = mock(McpToolQueryApi.class);
        agentService = new AgentServiceImpl(
                agentMapper, agentToolMapper, sessionQueryApi, modelQueryApi, mcpToolQueryApi);
    }

    @Test
    void shouldFullReplaceToolsWhenAllValid() {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        when(agentMapper.selectById(1L)).thenReturn(agent);
        when(mcpToolQueryApi.listAvailableToolIds(List.of(1L, 2L))).thenReturn(List.of(1L, 2L));

        AgentToolEntity oldTool = new AgentToolEntity();
        oldTool.setId(10L);
        oldTool.setAgentId(1L);
        oldTool.setToolId(1L);
        when(agentToolMapper.selectList(any())).thenReturn(List.of(oldTool));

        List<AgentToolResponse> result = agentService.updateTools(1L, List.of(1L, 2L));

        verify(agentToolMapper).deleteByIds(List.of(10L));
        verify(agentToolMapper, times(2)).insert(any(AgentToolEntity.class));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getToolId()).isEqualTo(1L);
        assertThat(result.get(1).getToolId()).isEqualTo(2L);
    }

    @Test
    void shouldRejectWhenMoreThanTenTools() {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        when(agentMapper.selectById(1L)).thenReturn(agent);

        List<Long> tooMany = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

        assertThatThrownBy(() -> agentService.updateTools(1L, tooMany))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("最多绑定 10 个工具");
    }

    @Test
    void shouldRejectWhenToolUnavailable() {
        AgentEntity agent = new AgentEntity();
        agent.setId(1L);
        when(agentMapper.selectById(1L)).thenReturn(agent);
        when(mcpToolQueryApi.listAvailableToolIds(List.of(1L, 2L))).thenReturn(List.of(1L));

        assertThatThrownBy(() -> agentService.updateTools(1L, List.of(1L, 2L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("toolId=2");
    }

    @Test
    void shouldBindToolsWhenCreatingAgent() {
        AgentCreateRequest request = new AgentCreateRequest();
        request.setName("客服助手");
        request.setSystemPrompt("你是客服助手");
        request.setModelConfigId(100L);
        request.setToolIds(List.of(1L, 2L));

        when(agentMapper.selectCount(any())).thenReturn(0L);
        when(modelQueryApi.isModelAvailable(100L)).thenReturn(true);
        when(mcpToolQueryApi.listAvailableToolIds(List.of(1L, 2L))).thenReturn(List.of(1L, 2L));
        doAnswer(invocation -> {
            AgentEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(agentMapper).insert(any(AgentEntity.class));

        AgentDetailResponse response = agentService.create(request);

        ArgumentCaptor<AgentToolEntity> captor = ArgumentCaptor.forClass(AgentToolEntity.class);
        verify(agentToolMapper, times(2)).insert(captor.capture());
        assertThat(response.getToolCount()).isEqualTo(2);
        assertThat(captor.getAllValues()).extracting(AgentToolEntity::getToolId)
                .containsExactly(1L, 2L);
    }

    @Test
    void shouldThrowAgentNotFoundWhenAgentMissing() {
        when(agentMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> agentService.updateTools(1L, List.of(1L)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("id=1");
    }
}
