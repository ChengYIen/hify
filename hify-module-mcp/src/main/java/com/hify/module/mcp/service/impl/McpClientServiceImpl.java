package com.hify.module.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.module.mcp.client.McpClientFactory;
import com.hify.module.mcp.controller.dto.McpToolResponse;
import com.hify.module.mcp.repository.McpServerMapper;
import com.hify.module.mcp.repository.entity.McpServerEntity;
import com.hify.module.mcp.service.McpClientService;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MCP 客户端调用实现，按调用创建/关闭 {@link McpSyncClient}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private final McpServerMapper mcpServerMapper;
    private final McpClientFactory mcpClientFactory;

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServerEntity server = requireServer(mcpServerId);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
                .name(toolName)
                .arguments(arguments != null ? arguments : Map.of())
                .build();
        try (McpSyncClient client = mcpClientFactory.create(server)) {
            McpSchema.CallToolResult result = client.callTool(request);
            if (Boolean.TRUE.equals(result.isError())) {
                String errorText = extractText(result);
                log.warn("MCP 工具调用返回错误: serverId={}, toolName={}, error={}",
                        mcpServerId, toolName, errorText);
                throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
                        StringUtils.hasText(errorText) ? errorText : ErrorCode.MCP_TOOL_CALL_FAILED.getMessage());
            }
            return extractText(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP 工具调用异常: serverId={}, toolName={}", mcpServerId, toolName, e);
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
                    ErrorCode.MCP_TOOL_CALL_FAILED.getMessage() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        return listToolResponses(mcpServerId).stream()
                .map(McpToolResponse::getToolName)
                .toList();
    }

    @Override
    public List<McpToolResponse> listToolResponses(Long mcpServerId) {
        McpServerEntity server = requireServer(mcpServerId);
        try (McpSyncClient client = mcpClientFactory.create(server)) {
            McpSchema.ListToolsResult result = client.listTools();
            if (result.tools() == null) {
                return List.of();
            }
            return result.tools().stream()
                    .map(this::toToolResponse)
                    .toList();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("MCP 工具列表获取失败: serverId={}", mcpServerId, e);
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND,
                    ErrorCode.MCP_SERVER_NOT_FOUND.getMessage() + ": " + e.getMessage(), e);
        }
    }

    private McpServerEntity requireServer(Long mcpServerId) {
        McpServerEntity server = mcpServerMapper.selectById(mcpServerId);
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "id=" + mcpServerId);
        }
        return server;
    }

    private McpToolResponse toToolResponse(McpSchema.Tool tool) {
        String inputSchema = "{}";
        if (tool.inputSchema() != null) {
            try {
                inputSchema = McpJsonDefaults.getMapper().writeValueAsString(tool.inputSchema());
            } catch (IOException e) {
                log.warn("MCP inputSchema 序列化失败: toolName={}", tool.name(), e);
            }
        }
        return McpToolResponse.builder()
                .toolName(tool.name())
                .description(tool.description())
                .inputSchema(inputSchema)
                .build();
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }
}
