package com.hify.module.mcp.client;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;

import java.util.Map;

/**
 * 透传 JSON Schema 校验器：SDK 需要注册一个校验器才能构建客户端，
 * 验收场景只用 TextContent 结果，不做结构化输出校验。
 */
public class NoopJsonSchemaValidator implements JsonSchemaValidator {

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object value) {
        return ValidationResponse.asValid("");
    }
}
