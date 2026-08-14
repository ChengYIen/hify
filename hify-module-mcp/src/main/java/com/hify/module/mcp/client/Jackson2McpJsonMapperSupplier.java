package com.hify.module.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;

/**
 * 通过 {@code META-INF/services} 向 MCP SDK 注册 Jackson 2 JSON 映射器.
 */
public class Jackson2McpJsonMapperSupplier implements McpJsonMapperSupplier {

    private final McpJsonMapper mapper = new Jackson2McpJsonMapper(new ObjectMapper());

    @Override
    public McpJsonMapper get() {
        return mapper;
    }
}
