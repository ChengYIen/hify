-- ============================================================
-- V4 - 业务表基线
-- 覆盖 provider、agent、conversation、knowledge、workflow 和 MCP 配置表。
-- 所有表使用 IF NOT EXISTS，便于从旧版手工建表环境平滑补齐。
-- ============================================================

CREATE TABLE IF NOT EXISTS hify_user (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    display_name VARCHAR(64) DEFAULT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_provider (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    provider_code VARCHAR(32) NOT NULL,
    auth_config JSON DEFAULT NULL,
    base_url VARCHAR(255) DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    health_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at DATETIME DEFAULT NULL,
    health_fail_reason VARCHAR(512) DEFAULT NULL,
    fail_count INT NOT NULL DEFAULT 0,
    last_success_at DATETIME DEFAULT NULL,
    discovery_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    last_synced_at DATETIME DEFAULT NULL,
    priority INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_provider_name (name),
    INDEX idx_provider_code (provider_code),
    INDEX idx_provider_status (status),
    INDEX idx_provider_health (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_provider_model (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT UNSIGNED NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) DEFAULT NULL,
    model_type VARCHAR(32) NOT NULL DEFAULT 'LLM',
    context_window INT UNSIGNED NOT NULL DEFAULT 8192,
    max_output INT UNSIGNED NOT NULL DEFAULT 4096,
    supports_vision TINYINT NOT NULL DEFAULT 0,
    supports_tools TINYINT NOT NULL DEFAULT 0,
    supports_streaming TINYINT NOT NULL DEFAULT 1,
    capabilities JSON DEFAULT NULL,
    priority INT NOT NULL DEFAULT 0,
    fallback_model_id BIGINT UNSIGNED DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_model_provider_name (provider_id, model_name),
    INDEX idx_model_provider (provider_id),
    INDEX idx_model_status (status),
    INDEX idx_model_type (model_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_provider_health (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT UNSIGNED NOT NULL,
    health_status VARCHAR(16) NOT NULL,
    response_time_ms INT DEFAULT NULL,
    fail_reason VARCHAR(512) DEFAULT NULL,
    alert_triggered TINYINT NOT NULL DEFAULT 0,
    checked_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_health_provider (provider_id),
    INDEX idx_health_checked (checked_at),
    INDEX idx_health_status (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_agent (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    avatar_url VARCHAR(255) DEFAULT NULL,
    system_prompt TEXT NOT NULL,
    model_id BIGINT UNSIGNED NOT NULL,
    workflow_id BIGINT UNSIGNED DEFAULT NULL,
    temperature DECIMAL(3,2) DEFAULT 0.70,
    max_tokens INT DEFAULT 4096,
    max_iterations INT NOT NULL DEFAULT 10,
    tools_enabled TINYINT NOT NULL DEFAULT 0,
    knowledge_ids JSON DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_by BIGINT UNSIGNED DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_agent_name (name),
    INDEX idx_agent_status (status),
    INDEX idx_agent_model (model_id),
    INDEX idx_agent_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_tool_definition (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tool_name VARCHAR(128) NOT NULL,
    tool_type VARCHAR(32) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    tool_config JSON DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tool_def_name (tool_name),
    INDEX idx_tool_def_type (tool_type),
    INDEX idx_tool_def_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_agent_tool (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT UNSIGNED NOT NULL,
    tool_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_agent_tool_agent_tool (agent_id, tool_id),
    INDEX idx_agent_tool_agent (agent_id),
    INDEX idx_agent_tool_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_mcp_server (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    transport VARCHAR(16) NOT NULL DEFAULT 'stdio',
    command VARCHAR(255) DEFAULT NULL,
    args JSON DEFAULT NULL,
    env_vars JSON DEFAULT NULL,
    url VARCHAR(255) DEFAULT NULL,
    headers JSON DEFAULT NULL,
    timeout_ms INT NOT NULL DEFAULT 30000,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_mcp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_mcp_tool (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mcp_server_id BIGINT UNSIGNED NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    input_schema JSON DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_mcp_tool_server_name (mcp_server_id, tool_name),
    INDEX idx_mcp_tool_server (mcp_server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_chat_session (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) DEFAULT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    agent_id BIGINT UNSIGNED DEFAULT NULL,
    model_id BIGINT UNSIGNED DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    message_count INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_session_user (user_id),
    INDEX idx_session_agent (agent_id),
    INDEX idx_session_status (status),
    INDEX idx_session_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_chat_message (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    model VARCHAR(128) DEFAULT NULL,
    token_usage JSON DEFAULT NULL,
    finish_reason VARCHAR(32) DEFAULT NULL,
    latency_ms INT DEFAULT NULL,
    tool_calls JSON DEFAULT NULL,
    tool_call_id VARCHAR(128) DEFAULT NULL,
    fallback TINYINT NOT NULL DEFAULT 0,
    seq INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_message_session (session_id),
    INDEX idx_message_session_seq (session_id, seq),
    INDEX idx_message_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_knowledge (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    doc_count INT NOT NULL DEFAULT 0,
    chunk_count INT NOT NULL DEFAULT 0,
    embedding_model_id BIGINT UNSIGNED DEFAULT NULL,
    created_by BIGINT UNSIGNED DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_knowledge_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_knowledge_document (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    knowledge_id BIGINT UNSIGNED NOT NULL,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    file_url VARCHAR(512) DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(512) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_doc_knowledge (knowledge_id),
    INDEX idx_doc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_workflow (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_workflow_status (status),
    INDEX idx_workflow_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_workflow_node (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT UNSIGNED NOT NULL,
    node_key VARCHAR(128) NOT NULL,
    node_name VARCHAR(255) DEFAULT NULL,
    node_type VARCHAR(32) NOT NULL,
    config JSON NOT NULL,
    position_x DOUBLE DEFAULT NULL,
    position_y DOUBLE DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_workflow_node_workflow (workflow_id),
    INDEX idx_workflow_node_type (node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_workflow_edge (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT UNSIGNED NOT NULL,
    edge_id VARCHAR(128) DEFAULT NULL,
    source_node_key VARCHAR(128) NOT NULL,
    edge_condition VARCHAR(64) DEFAULT NULL,
    target_node_key VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_workflow_edge_workflow (workflow_id),
    INDEX idx_workflow_edge_source (workflow_id, source_node_key),
    INDEX idx_workflow_edge_target (workflow_id, target_node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_workflow_execution (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT UNSIGNED NOT NULL,
    workflow_version INT NOT NULL DEFAULT 1,
    input_data JSON DEFAULT NULL,
    output_data JSON DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(512) DEFAULT NULL,
    started_at DATETIME DEFAULT NULL,
    finished_at DATETIME DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    triggered_by BIGINT UNSIGNED DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_exec_workflow (workflow_id),
    INDEX idx_exec_status (status),
    INDEX idx_exec_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    input TEXT,
    output TEXT,
    error VARCHAR(500),
    elapsed_ms INT,
    created_at DATETIME NOT NULL,
    finished_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow_node_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    outputs JSON,
    error VARCHAR(500),
    elapsed_ms INT,
    created_at DATETIME NOT NULL,
    finished_at DATETIME,
    KEY idx_node_run_run_id (workflow_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hify_workflow_node_execution (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    execution_id BIGINT UNSIGNED NOT NULL,
    node_id VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    input_data JSON DEFAULT NULL,
    output_data JSON DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(512) DEFAULT NULL,
    started_at DATETIME DEFAULT NULL,
    finished_at DATETIME DEFAULT NULL,
    duration_ms INT DEFAULT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_node_exec (execution_id),
    INDEX idx_node_status (status),
    INDEX idx_node_type (node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
