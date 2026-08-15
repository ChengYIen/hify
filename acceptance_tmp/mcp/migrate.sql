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

DROP TABLE IF EXISTS hify_agent_tool;
CREATE TABLE hify_agent_tool (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    agent_id   BIGINT UNSIGNED NOT NULL COMMENT 'Agent ID',
    tool_id    BIGINT UNSIGNED NOT NULL COMMENT '工具 ID（关联 hify_mcp_tool.id）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_agent_tool_agent_tool (agent_id, tool_id),
    INDEX idx_agent_tool_agent (agent_id),
    INDEX idx_agent_tool_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具关联表';

UPDATE hify_agent
SET workflow_id = NULL,
    tools_enabled = 1,
    knowledge_ids = '[1]',
    system_prompt = '你是 Hify 商城的智能客服。当用户询问订单状态、物流进度、到哪了、发货、签收等问题时，必须调用 query_order 工具，userId 固定为 u001，orderId 从用户问题中提取数字。回答要简洁友好，先给结论。'
WHERE id = 1;

UPDATE hify_chat_session
SET agent_id = 2
WHERE id = 4;
