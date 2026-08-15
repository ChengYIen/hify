package com.hify.module.mcp.service.impl;

import com.hify.module.mcp.controller.dto.McpDebugRequest;
import com.hify.module.mcp.controller.dto.McpDebugResponse;
import com.hify.module.mcp.service.McpClientService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link McpDebugServiceImpl}.
 */
class McpDebugServiceImplTest {

    private final McpClientService mcpClientService = mock(McpClientService.class);
    private final McpDebugServiceImpl debugService = new McpDebugServiceImpl(mcpClientService);

    @Test
    void debugDelegatesToClientServiceAndMeasuresElapsed() {
        when(mcpClientService.callTool(10L, "query_order", Map.of("orderId", "A1")))
                .thenReturn("{\"status\":\"ok\"}");

        McpDebugRequest request = new McpDebugRequest();
        request.setToolName("query_order");
        request.setArguments(Map.of("orderId", "A1"));

        McpDebugResponse response = debugService.debug(10L, request);

        assertThat(response.getResult()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(response.getElapsedMs()).isGreaterThanOrEqualTo(0);
        verify(mcpClientService).callTool(10L, "query_order", Map.of("orderId", "A1"));
    }
}
