package com.hify.module.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.client.McpClientFactory;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.service.McpClientService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpClientServiceImplTest {

    @Mock
    private McpServerMapper mcpServerMapper;
    @Mock
    private McpClientFactory mcpClientFactory;

    private McpClientService service;

    @BeforeEach
    void setUp() {
        service = new McpClientServiceImpl(mcpServerMapper, mcpClientFactory);
    }

    @Test
    void should_callRemoteToolAndReturnText_whenToolSucceeds() {
        McpServerEntity server = server();
        McpSyncClient client = mock(McpSyncClient.class);
        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(mcpClientFactory.create(server)).thenReturn(client);
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .addTextContent("订单 A1 已发货")
                .isError(false)
                .build());

        String result = service.callTool(10L, "query_order", Map.of("orderId", "A1"));

        assertThat(result).isEqualTo("订单 A1 已发货");
        ArgumentCaptor<McpSchema.CallToolRequest> captor =
                ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("query_order");
        assertThat(captor.getValue().arguments()).containsEntry("orderId", "A1");
    }

    @Test
    void should_throwBizException_whenRemoteToolReturnsError() {
        McpServerEntity server = server();
        McpSyncClient client = mock(McpSyncClient.class);
        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(mcpClientFactory.create(server)).thenReturn(client);
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .addTextContent("订单不存在")
                .isError(true)
                .build());

        assertThatThrownBy(() -> service.callTool(10L, "query_order", Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException biz = (BizException) e;
                    assertThat(biz.getErrorCode()).isEqualTo(ErrorCode.MCP_TOOL_CALL_FAILED);
                    assertThat(biz.getMessage()).contains("订单不存在");
                });
    }

    @Test
    void should_joinMultipleTextContents_whenRemoteReturnsSeveralBlocks() {
        McpServerEntity server = server();
        McpSyncClient client = mock(McpSyncClient.class);
        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(mcpClientFactory.create(server)).thenReturn(client);
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("第一段"), new McpSchema.TextContent("第二段")))
                .isError(false)
                .build());

        String result = service.callTool(10L, "query_order", Map.of());

        assertThat(result).isEqualTo("第一段\n第二段");
    }

    @Test
    void should_wrapConnectFailureIntoBizException() {
        McpServerEntity server = server();
        McpSyncClient client = mock(McpSyncClient.class);
        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(mcpClientFactory.create(server)).thenReturn(client);
        when(client.callTool(any())).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> service.callTool(10L, "query_order", Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    void should_throwNotFound_whenServerMissing() {
        when(mcpServerMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.callTool(10L, "query_order", Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MCP_SERVER_NOT_FOUND));
    }

    @Test
    void should_mapToolMetadata_whenListingTools() {
        McpServerEntity server = server();
        McpSyncClient client = mock(McpSyncClient.class);
        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(mcpClientFactory.create(server)).thenReturn(client);
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of("orderId", Map.of("type", "string")),
                List.of("orderId"), false, Map.of(), Map.of());
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(
                McpSchema.Tool.builder()
                        .name("query_order")
                        .description("查询订单")
                        .inputSchema(schema)
                        .build()), null));

        List<McpToolResponse> responses = service.listToolResponses(10L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getToolName()).isEqualTo("query_order");
        assertThat(responses.get(0).getDescription()).isEqualTo("查询订单");
        assertThat(responses.get(0).getInputSchema()).contains("object");
    }

    @Test
    void should_returnEmptyTools_whenRemoteReturnsNullTools() {
        McpServerEntity server = server();
        McpSyncClient client = mock(McpSyncClient.class);
        when(mcpServerMapper.selectById(10L)).thenReturn(server);
        when(mcpClientFactory.create(server)).thenReturn(client);
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(null, null));

        assertThat(service.listToolResponses(10L)).isEmpty();
        assertThat(service.listTools(10L)).isEmpty();
    }

    private McpServerEntity server() {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(10L);
        return entity;
    }
}
