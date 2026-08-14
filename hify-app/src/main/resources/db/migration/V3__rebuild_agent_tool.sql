DROP TABLE IF EXISTS hify_agent_tool;

CREATE TABLE hify_agent_tool (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    agent_id   BIGINT UNSIGNED NOT NULL COMMENT 'Agent ID（关联 hify_agent.id）',
    tool_id    BIGINT UNSIGNED NOT NULL COMMENT '工具 ID（关联 hify_mcp_tool.id）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_agent_tool_agent_tool (agent_id, tool_id),
    INDEX idx_agent_tool_agent (agent_id),
    INDEX idx_agent_tool_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具关联表';
