CREATE TABLE IF NOT EXISTS hify_mcp_tool (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mcp_server_id   BIGINT UNSIGNED NOT NULL COMMENT '所属 MCP Server ID',
    tool_name       VARCHAR(128)    NOT NULL COMMENT '工具名称',
    description     VARCHAR(512)    DEFAULT NULL COMMENT '工具描述',
    input_schema    JSON            DEFAULT NULL COMMENT '工具入参 JSON Schema',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_mcp_tool_server_name (mcp_server_id, tool_name),
    INDEX idx_mcp_tool_server (mcp_server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 工具表';
