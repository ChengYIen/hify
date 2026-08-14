package com.hify.module.mcp.client;

import com.hify.module.mcp.repository.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

/**
 * 基于 Streamable HTTP 传输的 MCP 客户端工厂.
 */
@Component
public class DefaultMcpClientFactory implements McpClientFactory {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_ENDPOINT_PATH = "/mcp";

    @Override
    public McpSyncClient create(McpServerEntity server) {
        HttpClientStreamableHttpTransport transport = buildTransport(server.getUrl());
        return McpClient.sync(transport)
                .requestTimeout(DEFAULT_REQUEST_TIMEOUT)
                .clientInfo(new McpSchema.Implementation("Hify", "1.0"))
                .build();
    }

    private HttpClientStreamableHttpTransport buildTransport(String endpoint) {
        URI uri = URI.create(endpoint);
        String baseUri = uri.getScheme() + "://" + uri.getAuthority();
        String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : DEFAULT_ENDPOINT_PATH;
        return HttpClientStreamableHttpTransport.builder(baseUri)
                .endpoint(path)
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .build();
    }
}
