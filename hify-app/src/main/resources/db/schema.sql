-- ============================================================
-- Hify 业务表建表 DDL
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4  引擎：InnoDB
-- 规范：id BIGINT UNSIGNED 自增 / created_at + updated_at DATETIME
--       deleted TINYINT 逻辑删除 / 不建物理外键
-- ============================================================

-- ============================================================
-- 1. 用户表（一期 MVP，从简）
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_user (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL COMMENT '用户名',
    password     VARCHAR(255) NOT NULL COMMENT '密码(bcrypt)',
    display_name VARCHAR(64)  DEFAULT NULL COMMENT '显示名',
    role         VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN / USER',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 模型提供商实例表
-- ============================================================
-- 设计要点：
--   1. provider_code 不设唯一约束 → 同一类型可以有多个实例（如两个 OpenAI 账号）
--   2. auth_config JSON 按 provider_code 存不同鉴权结构，新供应商零改表
--   3. health_status 由定时健康检查任务更新，与用户开关 status 分离
--   4. discovery_type=AUTO 时，系统调用 provider API 自动同步模型列表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_provider (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL COMMENT '实例名称（如 OpenAI 个人号 / 公司 Azure）',
    description     VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
    provider_code   VARCHAR(32)  NOT NULL COMMENT '适配器编码: openai / claude / gemini / ollama',
    --
    -- 鉴权（auth_config JSON，按 provider_code 存不同结构）
    --   openai / openai_compatible:  {"apiKey": "sk-xxx"}
    --   anthropic:                   {"apiKey": "sk-ant-xxx", "anthropicVersion": "2023-06-01"}
    --   gemini:                      {"apiKey": "xxx"}
    --   azure:                       {"apiKey": "xxx"}
    --   ollama / vllm:               {}
    -- 未来新增供应商直接写新的 JSON schema，零改表
    -- ============================================================
    auth_config     JSON         DEFAULT NULL COMMENT '鉴权配置（JSON，按供应商类型存不同结构）',
    --
    -- 端点
    -- ============================================================
    base_url        VARCHAR(255) DEFAULT NULL COMMENT 'API 基础地址，为 NULL 则用适配器内置默认值',
    --
    -- 状态（用户开关 vs 实际健康，二者独立）
    -- ============================================================
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '用户开关: ENABLED / DISABLED',
    health_status   VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '健康状态: HEALTHY / UNHEALTHY / DEGRADED / UNKNOWN',
    last_health_check_at DATETIME DEFAULT NULL COMMENT '最近一次健康检查时间',
    health_fail_reason   VARCHAR(512) DEFAULT NULL COMMENT '最近一次健康检查失败原因',
    fail_count       INT          NOT NULL DEFAULT 0 COMMENT '连续失败次数（成功归零，≥3 标记 UNHEALTHY）',
    last_success_at  DATETIME     DEFAULT NULL COMMENT '最近一次健康检查成功时间',
    --
    -- 模型管理
    -- ============================================================
    discovery_type  VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '模型发现方式: AUTO(调API自动同步) / MANUAL(手动维护)',
    last_synced_at  DATETIME     DEFAULT NULL COMMENT '最近一次自动同步模型列表的时间',
    --
    -- 排序 & 元数据
    -- ============================================================
    priority        INT          NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先（多实例时用于负载/降级排序）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_provider_name (name),
    INDEX idx_provider_code (provider_code),
    INDEX idx_provider_status (status),
    INDEX idx_provider_health (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型提供商实例表';

-- ============================================================
-- 3. 模型配置表
-- ============================================================
-- 设计要点：
--   1. 每个 provider 实例下可有多个模型，由 provider_id 关联
--   2. supports_* 字段保持 TINYINT（高频查询条件），其余能力放 capabilities JSON
--   3. fallback_model_id 构建同 provider 内的降级链路
--   4. priority 决定 Agent 未指定模型时的默认选择（最高优先级的启用模型）
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_provider_model (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    provider_id     BIGINT UNSIGNED NOT NULL COMMENT '所属提供商实例 ID（关联 hify_provider.id）',
    model_name      VARCHAR(128) NOT NULL COMMENT 'API 模型标识符（如 gpt-4o / claude-sonnet-4-20250514 / gemini-2.5-pro）',
    display_name    VARCHAR(128) DEFAULT NULL COMMENT '前端展示名（如 GPT-4o / Claude Sonnet 4），为 NULL 则用 model_name',
    model_type      VARCHAR(32)  NOT NULL DEFAULT 'LLM' COMMENT '模型类型: LLM / EMBEDDING / IMAGE / TTS / RERANK',
    --
    -- 容量参数
    -- ============================================================
    context_window  INT UNSIGNED NOT NULL DEFAULT 8192 COMMENT '上下文窗口 Token 数',
    max_output      INT UNSIGNED NOT NULL DEFAULT 4096 COMMENT '最大输出 Token 数',
    --
    -- 能力标记（高频查询 → TINYINT；低频扩展 → capabilities JSON）
    -- ============================================================
    supports_vision     TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持图片输入 0=否 1=是',
    supports_tools      TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持工具调用(function calling) 0=否 1=是',
    supports_streaming  TINYINT NOT NULL DEFAULT 1 COMMENT '是否支持流式输出(SSE) 0=否 1=是',
    capabilities        JSON    DEFAULT NULL COMMENT '扩展能力（如 supports_structured_output / supports_prompt_caching / audio 等）',
    --
    -- 降级 & 优先级
    -- ============================================================
    priority            INT    NOT NULL DEFAULT 0 COMMENT '优先级，数值越大越优先（同一 provider 内选默认模型时用）',
    fallback_model_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '降级备选模型 ID（同 provider 内，主模型不可用时自动切换）',
    --
    -- 状态 & 元数据
    -- ============================================================
    status          VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED / DISABLED',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_model_provider_name (provider_id, model_name),
    INDEX idx_model_provider (provider_id),
    INDEX idx_model_status (status),
    INDEX idx_model_type (model_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- ============================================================
-- 4. 提供商健康检查记录表
-- ============================================================
-- 设计要点：
--   1. 每次健康检查产生一条记录，用于追踪可用性历史
--   2. 与 hify_provider.health_status（当前快照）分离——当前状态放 Provider，历史放本表
--   3. 定时任务每分钟检查一次，成功/失败都记录
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_provider_health (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    provider_id     BIGINT UNSIGNED NOT NULL COMMENT '所属提供商实例 ID（关联 hify_provider.id）',
    health_status   VARCHAR(16)  NOT NULL COMMENT '健康状态: HEALTHY / UNHEALTHY / DEGRADED / UNKNOWN',
    response_time_ms INT         DEFAULT NULL COMMENT '响应时间（毫秒），-1 表示不可达',
    fail_reason     VARCHAR(512) DEFAULT NULL COMMENT '失败原因（HEALTHY 时为 NULL）',
    alert_triggered TINYINT      NOT NULL DEFAULT 0 COMMENT '是否触发告警 0=否 1=是',
    checked_at      DATETIME     NOT NULL COMMENT '健康检查执行时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_health_provider (provider_id),
    INDEX idx_health_checked (checked_at),
    INDEX idx_health_status (health_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提供商健康检查记录表';

-- ============================================================
-- 5. Agent 配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_agent (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT 'Agent 描述',
    avatar_url      VARCHAR(255) DEFAULT NULL COMMENT '头像 URL',
    system_prompt   TEXT         NOT NULL COMMENT '系统提示词',
    model_id        BIGINT UNSIGNED NOT NULL COMMENT '默认模型 ID（关联 hify_provider_model.id）',
    temperature     DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度 0.00–2.00',
    max_tokens      INT          DEFAULT 4096 COMMENT '最大输出 Token',
    max_iterations  INT          NOT NULL DEFAULT 10 COMMENT 'Agent 循环最大迭代次数',
    tools_enabled   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否启用工具调用 0=否 1=是',
    knowledge_ids   JSON         DEFAULT NULL COMMENT '关联知识库 ID 列表（JSON 数组）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED / DISABLED / DRAFT',
    created_by      BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户 ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_agent_name (name),
    INDEX idx_agent_status (status),
    INDEX idx_agent_model (model_id),
    INDEX idx_agent_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 配置表';

-- ============================================================
-- 6. 工具定义表（可复用工具目录）
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_tool_definition (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tool_name       VARCHAR(128) NOT NULL COMMENT '工具名称',
    tool_type       VARCHAR(32)  NOT NULL COMMENT '工具类型: MCP / BUILTIN / HTTP',
    description     VARCHAR(512) DEFAULT NULL COMMENT '工具描述',
    tool_config     JSON         DEFAULT NULL COMMENT '工具配置（JSON，含参数描述、端点等）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED / DISABLED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    UNIQUE KEY uk_tool_def_name (tool_name),
    INDEX idx_tool_def_type (tool_type),
    INDEX idx_tool_def_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具定义表';

-- ============================================================
-- 7. Agent 工具关联表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_agent_tool (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    agent_id        BIGINT UNSIGNED NOT NULL COMMENT 'Agent ID（关联 hify_agent.id）',
    tool_name       VARCHAR(128) NOT NULL COMMENT '工具名称',
    tool_type       VARCHAR(32)  NOT NULL COMMENT '工具类型: MCP / BUILTIN / HTTP',
    tool_config     JSON         DEFAULT NULL COMMENT '工具配置（JSON，含参数描述、端点等）',
    priority        INT          NOT NULL DEFAULT 0 COMMENT '排序优先级',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_agent_tool_agent (agent_id),
    INDEX idx_agent_tool_type (tool_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 工具关联表';

-- ============================================================
-- 8. MCP 服务配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_mcp_server (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL COMMENT 'MCP 服务名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '服务描述',
    transport       VARCHAR(16)  NOT NULL DEFAULT 'stdio' COMMENT '传输协议: stdio / sse / streamable',
    command         VARCHAR(255) DEFAULT NULL COMMENT '启动命令（stdio 模式）',
    args            JSON         DEFAULT NULL COMMENT '命令参数（JSON 数组）',
    env_vars        JSON         DEFAULT NULL COMMENT '环境变量（JSON 对象）',
    url             VARCHAR(255) DEFAULT NULL COMMENT '服务 URL（sse/streamable 模式）',
    headers         JSON         DEFAULT NULL COMMENT '请求头（JSON 对象）',
    timeout_ms      INT          NOT NULL DEFAULT 30000 COMMENT '超时时间（毫秒）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED / DISABLED',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_mcp_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP 服务配置表';

-- ============================================================
-- 9. 对话会话表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_chat_session (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(255) DEFAULT NULL COMMENT '会话标题（默认取首条用户消息摘要）',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户 ID',
    agent_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '使用的 Agent ID（为空则为自由对话）',
    model_id        BIGINT UNSIGNED DEFAULT NULL COMMENT '使用的模型 ID',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE / ARCHIVED / ERROR',
    message_count   INT          NOT NULL DEFAULT 0 COMMENT '消息总数（冗余，避免 COUNT）',
    total_tokens    INT          NOT NULL DEFAULT 0 COMMENT '累计 Token 消耗',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_session_user (user_id),
    INDEX idx_session_agent (agent_id),
    INDEX idx_session_status (status),
    INDEX idx_session_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- ============================================================
-- 10. 对话消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_chat_message (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT UNSIGNED NOT NULL COMMENT '所属会话 ID（关联 hify_chat_session.id）',
    role            VARCHAR(16)  NOT NULL COMMENT '角色: system / user / assistant / tool',
    content         TEXT         NOT NULL COMMENT '消息内容',
    model           VARCHAR(128) DEFAULT NULL COMMENT '实际使用的模型名称（仅 assistant 消息）',
    token_usage     JSON         DEFAULT NULL COMMENT 'Token 用量（promptTokens / completionTokens / totalTokens）',
    finish_reason   VARCHAR(32)  DEFAULT NULL COMMENT '停止原因: stop / length / tool_calls / content_filter',
    tool_calls      JSON         DEFAULT NULL COMMENT '工具调用请求（模型要求调用工具时）',
    tool_call_id    VARCHAR(128) DEFAULT NULL COMMENT '工具调用 ID（role=tool 时回填）',
    fallback        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否降级模型响应 0=否 1=是',
    seq             INT          NOT NULL DEFAULT 0 COMMENT '消息序号（会话内递增）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_message_session (session_id),
    INDEX idx_message_session_seq (session_id, seq),
    INDEX idx_message_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话消息表';

-- ============================================================
-- 11. 知识库表（MySQL 侧，仅存元数据；向量存在 PostgreSQL pgvector）
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_knowledge (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL COMMENT '知识库名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '知识库描述',
    doc_count       INT          NOT NULL DEFAULT 0 COMMENT '文档数量（冗余）',
    chunk_count     INT          NOT NULL DEFAULT 0 COMMENT '向量块总数（冗余）',
    embedding_model VARCHAR(128) NOT NULL DEFAULT 'text-embedding-ada-002' COMMENT '使用的 Embedding 模型',
    created_by      BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户 ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_knowledge_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库元数据表';

-- ============================================================
-- 12. 知识库文档表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_knowledge_document (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    knowledge_id    BIGINT UNSIGNED NOT NULL COMMENT '所属知识库 ID',
    filename        VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_type       VARCHAR(16)  NOT NULL COMMENT '文件类型: PDF / TXT / MD / DOCX',
    file_size       BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    file_url        VARCHAR(512) DEFAULT NULL COMMENT '文件存储 URL',
    parse_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '解析状态: PENDING / PARSING / COMPLETED / FAILED',
    chunk_count     INT          NOT NULL DEFAULT 0 COMMENT '切分块数',
    error_message   VARCHAR(512) DEFAULT NULL COMMENT '解析失败原因',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_doc_knowledge (knowledge_id),
    INDEX idx_doc_parse_status (parse_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- ============================================================
-- 13. 工作流定义表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_workflow (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL COMMENT '工作流名称',
    description     VARCHAR(512) DEFAULT NULL COMMENT '工作流描述',
    definition      JSON         NOT NULL COMMENT '工作流定义（节点、边、输入输出）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT / PUBLISHED / DISABLED',
    version         INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    created_by      BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人用户 ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_workflow_status (status),
    INDEX idx_workflow_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- ============================================================
-- 14. 工作流执行记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_workflow_execution (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT UNSIGNED NOT NULL COMMENT '工作流 ID',
    workflow_version INT         NOT NULL DEFAULT 1 COMMENT '执行时的工作流版本',
    input_data      JSON         DEFAULT NULL COMMENT '输入参数',
    output_data     JSON         DEFAULT NULL COMMENT '输出结果',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / RUNNING / COMPLETED / FAILED / CANCELLED',
    error_message   VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    started_at      DATETIME     DEFAULT NULL COMMENT '开始执行时间',
    finished_at     DATETIME     DEFAULT NULL COMMENT '结束执行时间',
    duration_ms     INT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    triggered_by    BIGINT UNSIGNED DEFAULT NULL COMMENT '触发人用户 ID',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_exec_workflow (workflow_id),
    INDEX idx_exec_status (status),
    INDEX idx_exec_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行记录表';

-- ============================================================
-- 15. 工作流节点执行详情表
-- ============================================================
CREATE TABLE IF NOT EXISTS hify_workflow_node_execution (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    execution_id      BIGINT UNSIGNED NOT NULL COMMENT '工作流执行 ID',
    node_id           VARCHAR(128) NOT NULL COMMENT '节点 ID（对应定义中的节点标识）',
    node_type         VARCHAR(32)  NOT NULL COMMENT '节点类型: LLM / CONDITION / TOOL / START / END',
    input_data        JSON         DEFAULT NULL COMMENT '节点输入',
    output_data       JSON         DEFAULT NULL COMMENT '节点输出',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING / RUNNING / COMPLETED / FAILED / SKIPPED',
    error_message     VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
    started_at        DATETIME     DEFAULT NULL COMMENT '开始执行时间',
    finished_at       DATETIME     DEFAULT NULL COMMENT '结束执行时间',
    duration_ms       INT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    retry_count       INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=未删 1=已删',
    INDEX idx_node_exec (execution_id),
    INDEX idx_node_status (status),
    INDEX idx_node_type (node_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流节点执行详情表';
