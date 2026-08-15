package com.hify.module.mcp.service.impl;

import com.hify.module.mcp.controller.dto.McpDebugRequest;
import com.hify.module.mcp.controller.dto.McpDebugResponse;
import com.hify.module.mcp.service.McpClientService;
import com.hify.module.mcp.service.McpDebugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MCP tool debug service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpDebugServiceImpl implements McpDebugService {

    private final McpClientService mcpClientService;

    @Override
    public McpDebugResponse debug(Long mcpServerId, McpDebugRequest request) {
        long start = System.currentTimeMillis();
        String result = mcpClientService.callTool(
                mcpServerId, request.getToolName(), request.getArguments());
        long elapsedMs = System.currentTimeMillis() - start;
        log.info("MCP debug call finished: serverId={}, toolName={}, elapsedMs={}",
                mcpServerId, request.getToolName(), elapsedMs);
        return McpDebugResponse.builder()
                .result(result)
                .elapsedMs(elapsedMs)
                .build();
    }
}
