package com.hify.module.mcp.client;

import com.hify.module.mcp.repository.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpSyncClient;

/**
 * MCP 客户端工厂，每次调用创建独立 {@link McpSyncClient}.
 */
public interface McpClientFactory {

    McpSyncClient create(McpServerEntity server);
}
