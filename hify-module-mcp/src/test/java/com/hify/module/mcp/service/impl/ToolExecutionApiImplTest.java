package com.hify.module.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.service.McpClientService;
import com.hify.shared.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * MCP 工具执行契约单测.
 */
@ExtendWith(MockitoExtension.class)
class ToolExecutionApiImplTest {

    @Mock
    private McpClientService mcpClientService;

    private ToolExecutionApiImpl executionApi;

    @BeforeEach
    void setUp() {
        executionApi = new ToolExecutionApiImpl(mcpClientService);
    }

    @Test
    void shouldReturnSuccessWhenToolCallSucceeds() {
        when(mcpClientService.callTool(10L, "query_order", Map.of("orderId", "A1")))
                .thenReturn("订单 A1 已发货");

        ToolResultDTO result = executionApi.execute(10L, "query_order", Map.of("orderId", "A1"));

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getContent()).isEqualTo("订单 A1 已发货");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void shouldReturnErrorWhenToolCallFails() {
        when(mcpClientService.callTool(10L, "query_order", Map.of()))
                .thenThrow(new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "MCP 服务器连接失败"));

        ToolResultDTO result = executionApi.execute(10L, "query_order", Map.of());

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("MCP 服务器连接失败");
    }
}
