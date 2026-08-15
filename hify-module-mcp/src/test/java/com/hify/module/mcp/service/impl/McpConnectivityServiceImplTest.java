package com.hify.module.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.controller.dto.ConnectionTestResult;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.McpToolMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.repository.entity.McpToolEntity;
import com.hify.module.mcp.service.McpClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpConnectivityServiceImplTest {

    @Mock
    private McpServerMapper mcpServerMapper;
    @Mock
    private McpToolMapper mcpToolMapper;
    @Mock
    private McpClientService mcpClientService;

    private McpConnectivityServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new McpConnectivityServiceImpl(mcpServerMapper, mcpToolMapper, mcpClientService);
    }

    @Test
    void should_insertNewTools_whenFirstConnectivityTestSucceeds() {
        when(mcpServerMapper.selectById(10L)).thenReturn(server());
        when(mcpClientService.listToolResponses(10L))
                .thenReturn(List.of(toolResponse("query_order", "查询订单", "{\"type\":\"object\"}")));
        when(mcpToolMapper.selectAllByServerId(10L)).thenReturn(List.of());

        ConnectionTestResult result = service.testConnection(10L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getToolCount()).isEqualTo(1);
        ArgumentCaptor<McpToolEntity> captor = ArgumentCaptor.forClass(McpToolEntity.class);
        verify(mcpToolMapper).insert(captor.capture());
        assertThat(captor.getValue().getMcpServerId()).isEqualTo(10L);
        assertThat(captor.getValue().getToolName()).isEqualTo("query_order");
        assertThat(captor.getValue().getInputSchema()).contains("object");
    }

    @Test
    void should_restoreAndUpdateExistingTool_whenToolStillExists() {
        McpToolEntity existing = toolEntity(1L, "query_order", 0);
        when(mcpServerMapper.selectById(10L)).thenReturn(server());
        when(mcpClientService.listToolResponses(10L))
                .thenReturn(List.of(toolResponse("query_order", "新描述", "{}")));
        when(mcpToolMapper.selectAllByServerId(10L)).thenReturn(List.of(existing));

        ConnectionTestResult result = service.testConnection(10L);

        assertThat(result.isSuccess()).isTrue();
        verify(mcpToolMapper, never()).insert(any(McpToolEntity.class));
        verify(mcpToolMapper).restoreAndUpdate(existing);
        assertThat(existing.getDescription()).isEqualTo("新描述");
    }

    @Test
    void should_restoreDeletedTool_whenToolReappears() {
        McpToolEntity deleted = toolEntity(1L, "query_order", 1);
        when(mcpServerMapper.selectById(10L)).thenReturn(server());
        when(mcpClientService.listToolResponses(10L))
                .thenReturn(List.of(toolResponse("query_order", "回来", "{}")));
        when(mcpToolMapper.selectAllByServerId(10L)).thenReturn(List.of(deleted));

        ConnectionTestResult result = service.testConnection(10L);

        assertThat(result.isSuccess()).isTrue();
        verify(mcpToolMapper, never()).insert(any(McpToolEntity.class));
        verify(mcpToolMapper).restoreAndUpdate(deleted);
    }

    @Test
    void should_deleteTool_whenRemoteListNoLongerContainsIt() {
        McpToolEntity oldTool = toolEntity(1L, "old_tool", 0);
        when(mcpServerMapper.selectById(10L)).thenReturn(server());
        when(mcpClientService.listToolResponses(10L))
                .thenReturn(List.of(toolResponse("new_tool", "新工具", "{}")));
        when(mcpToolMapper.selectAllByServerId(10L)).thenReturn(List.of(oldTool));

        ConnectionTestResult result = service.testConnection(10L);

        assertThat(result.isSuccess()).isTrue();
        verify(mcpToolMapper).deleteById((Serializable) 1L);
        verify(mcpToolMapper).insert(any(McpToolEntity.class));
    }

    @Test
    void should_keepExistingTools_whenConnectivityTestFails() {
        when(mcpServerMapper.selectById(10L)).thenReturn(server());
        when(mcpClientService.listToolResponses(10L))
                .thenThrow(new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "远端不可达"));

        ConnectionTestResult result = service.testConnection(10L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getToolCount()).isZero();
        assertThat(result.getErrorMessage()).contains("远端不可达");
        verify(mcpToolMapper, never()).insert(any(McpToolEntity.class));
        verify(mcpToolMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void should_returnFailure_whenDuplicateToolInsertConflicts() {
        when(mcpServerMapper.selectById(10L)).thenReturn(server());
        when(mcpClientService.listToolResponses(10L))
                .thenReturn(List.of(
                        toolResponse("tool_a", "a", "{}"),
                        toolResponse("tool_b", "b", "{}")));
        when(mcpToolMapper.selectAllByServerId(10L)).thenReturn(List.of());
        AtomicInteger insertCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (insertCount.incrementAndGet() == 2) {
                throw new DuplicateKeyException("Duplicate entry '10-tool_b'");
            }
            return 1;
        }).when(mcpToolMapper).insert(any(McpToolEntity.class));

        ConnectionTestResult result = service.testConnection(10L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Duplicate");
    }

    private McpServerEntity server() {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(10L);
        return entity;
    }

    private McpToolResponse toolResponse(String name, String description, String inputSchema) {
        return McpToolResponse.builder()
                .toolName(name)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    private McpToolEntity toolEntity(Long id, String toolName, int deleted) {
        McpToolEntity entity = new McpToolEntity();
        entity.setId(id);
        entity.setMcpServerId(10L);
        entity.setToolName(toolName);
        entity.setDeleted(deleted);
        return entity;
    }
}
