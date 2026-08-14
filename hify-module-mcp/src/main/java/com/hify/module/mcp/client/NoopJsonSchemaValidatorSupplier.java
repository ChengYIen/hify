package com.hify.module.mcp.client;

import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidatorSupplier;

/**
 * 通过 {@code META-INF/services} 向 MCP SDK 注册透传校验器.
 */
public class NoopJsonSchemaValidatorSupplier implements JsonSchemaValidatorSupplier {

    private final JsonSchemaValidator validator = new NoopJsonSchemaValidator();

    @Override
    public JsonSchemaValidator get() {
        return validator;
    }
}
