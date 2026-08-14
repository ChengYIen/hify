package com.hify.module.mcp.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.McpToolMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.shared.tool.dto.AgentBoundToolDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP 工具可用性查询单测.
 */
class McpToolQueryApiImplTest {

    private McpToolMapper mcpToolMapper;
    private McpServerMapper mcpServerMapper;
    private McpToolQueryApiImpl queryApi;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, McpToolEntity.class);
        TableInfoHelper.initTableInfo(assistant, McpServerEntity.class);

        mcpToolMapper = mock(McpToolMapper.class);
        mcpServerMapper = mock(McpServerMapper.class);
        queryApi = new McpToolQueryApiImpl(mcpToolMapper, mcpServerMapper);
    }

    @Test
    void shouldReturnEmptyWhenNoToolIds() {
        assertThat(queryApi.listAvailableToolIds(null)).isEmpty();
        assertThat(queryApi.listAvailableToolIds(List.of())).isEmpty();
    }

    @Test
    void shouldOnlyReturnToolsBelongingToEnabledServer() {
        McpToolEntity tool1 = tool(1L, 10L);
        McpToolEntity tool2 = tool(2L, 20L);
        when(mcpToolMapper.selectList(any())).thenReturn(List.of(tool1, tool2));

        McpServerEntity enabled = server(10L, "ENABLED");
        McpServerEntity disabled = server(20L, "DISABLED");
        when(mcpServerMapper.selectList(any())).thenReturn(List.of(enabled, disabled));

        List<Long> result = queryApi.listAvailableToolIds(List.of(1L, 2L));

        assertThat(result).containsExactly(1L);
    }

    @Test
    void shouldReturnBoundToolsByAgent() {
        AgentBoundToolDTO tool = AgentBoundToolDTO.builder()
                .toolName("query_order")
                .description("查询订单")
                .inputSchema("{}")
                .mcpServerId(10L)
                .build();
        when(mcpToolMapper.selectBoundTools(2L)).thenReturn(List.of(tool));

        assertThat(queryApi.listBoundTools(2L)).containsExactly(tool);
        assertThat(queryApi.listBoundTools(null)).isEmpty();
    }

    private McpToolEntity tool(Long id, Long serverId) {
        McpToolEntity entity = new McpToolEntity();
        entity.setId(id);
        entity.setMcpServerId(serverId);
        return entity;
    }

    private McpServerEntity server(Long id, String status) {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(id);
        entity.setStatus(status);
        return entity;
    }
}
