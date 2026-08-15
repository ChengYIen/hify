CREATE TABLE IF NOT EXISTS hify_provider (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(64)  NOT NULL,
    description         VARCHAR(255),
    provider_code       VARCHAR(32)  NOT NULL,
    auth_config         CLOB,
    base_url            VARCHAR(255),
    status              VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    health_status       VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    last_health_check_at TIMESTAMP,
    health_fail_reason  VARCHAR(512),
    fail_count          INT          NOT NULL DEFAULT 0,
    last_success_at     TIMESTAMP,
    discovery_type      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    last_synced_at      TIMESTAMP,
    priority            INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_provider_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS hify_provider_model (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id        BIGINT       NOT NULL,
    model_name         VARCHAR(128) NOT NULL,
    display_name       VARCHAR(128),
    model_type         VARCHAR(32)  NOT NULL DEFAULT 'LLM',
    context_window     INT          NOT NULL DEFAULT 8192,
    max_output         INT          NOT NULL DEFAULT 4096,
    supports_vision    TINYINT      NOT NULL DEFAULT 0,
    supports_tools     TINYINT      NOT NULL DEFAULT 0,
    supports_streaming TINYINT      NOT NULL DEFAULT 1,
    capabilities       CLOB,
    priority           INT          NOT NULL DEFAULT 0,
    fallback_model_id  BIGINT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_model_provider_name UNIQUE (provider_id, model_name)
);

CREATE TABLE IF NOT EXISTS hify_provider_health (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id     BIGINT       NOT NULL,
    health_status   VARCHAR(16)  NOT NULL,
    response_time_ms INT,
    fail_reason     VARCHAR(512),
    alert_triggered TINYINT      NOT NULL DEFAULT 0,
    checked_at      TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hify_agent (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    avatar_url      VARCHAR(255),
    system_prompt   CLOB         NOT NULL,
    model_id        BIGINT       NOT NULL,
    workflow_id     BIGINT,
    temperature     DECIMAL(3,2) DEFAULT 0.70,
    max_tokens      INT          DEFAULT 4096,
    max_iterations  INT          NOT NULL DEFAULT 10,
    tools_enabled   TINYINT      NOT NULL DEFAULT 0,
    knowledge_ids   CLOB,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS hify_chat_session (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(255),
    user_id         BIGINT       NOT NULL,
    agent_id        BIGINT,
    model_id        BIGINT,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    message_count   INT          NOT NULL DEFAULT 0,
    total_tokens    INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS hify_chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         CLOB         NOT NULL,
    model           VARCHAR(128),
    token_usage     CLOB,
    finish_reason   VARCHAR(32),
    latency_ms      INT,
    tool_calls      CLOB,
    tool_call_id    VARCHAR(128),
    fallback        TINYINT      NOT NULL DEFAULT 0,
    seq             INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
);
