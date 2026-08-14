package com.hify.module.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.module.mcp.service.McpClientService;
import com.hify.shared.tool.ToolExecutionApi;
import com.hify.shared.tool.dto.ToolResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工具执行契约实现，委托给 {@link McpClientService}.
 *
 * <p>工具调用失败不中断对话，统一转为 {@code success=false} 的错误结果，
 * 由对话引擎作为 tool 消息回填给 LLM。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolExecutionApiImpl implements ToolExecutionApi {

    private final McpClientService mcpClientService;

    @Override
    public ToolResultDTO execute(Long mcpServerId, String toolName, Map<String, Object> params) {
        try {
            String content = mcpClientService.callTool(mcpServerId, toolName, params);
            return ToolResultDTO.builder()
                    .success(true)
                    .content(content)
                    .build();
        } catch (BizException e) {
            log.warn("MCP 工具调用失败: serverId={}, toolName={}, msg={}",
                    mcpServerId, toolName, e.getMessage());
            return ToolResultDTO.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
