package com.hify.module.mcp.client;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;

import java.io.IOException;

/**
 * 基于项目内 Jackson 2 的 {@link McpJsonMapper} 实现.
 *
 * <p>MCP SDK 1.1.1 默认的 {@code mcp-json-jackson3} 使用 Jackson 3（tools.jackson），
 * 与 Spring Boot 的 Jackson 2 注解类同名冲突，运行时抛
 * {@code NoSuchMethodError: JsonProperty.isRequired()}。本实现让 SDK 走 Jackson 2。</p>
 */
public class Jackson2McpJsonMapper implements McpJsonMapper {

    private final ObjectMapper objectMapper;

    public Jackson2McpJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T readValue(String json, Class<T> clazz) throws IOException {
        return objectMapper.readValue(json, clazz);
    }

    @Override
    public <T> T readValue(byte[] bytes, Class<T> clazz) throws IOException {
        return objectMapper.readValue(bytes, clazz);
    }

    @Override
    public <T> T readValue(String json, TypeRef<T> typeRef) throws IOException {
        return objectMapper.readValue(json, toJavaType(typeRef));
    }

    @Override
    public <T> T readValue(byte[] bytes, TypeRef<T> typeRef) throws IOException {
        return objectMapper.readValue(bytes, toJavaType(typeRef));
    }

    @Override
    public <T> T convertValue(Object value, Class<T> clazz) {
        return objectMapper.convertValue(value, clazz);
    }

    @Override
    public <T> T convertValue(Object value, TypeRef<T> typeRef) {
        return objectMapper.convertValue(value, toJavaType(typeRef));
    }

    @Override
    public String writeValueAsString(Object value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    @Override
    public byte[] writeValueAsBytes(Object value) throws IOException {
        return objectMapper.writeValueAsBytes(value);
    }

    private JavaType toJavaType(TypeRef<?> typeRef) {
        return objectMapper.getTypeFactory().constructType(typeRef.getType());
    }
}
