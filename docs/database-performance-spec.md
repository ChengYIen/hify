# Hify 数据库性能规范 v1.0

> **适用：** MySQL 8.x（业务数据）+ PostgreSQL 16 + pgvector（向量数据）。
> **原则：** 每条规则都是可执行的判断标准，不含"视情况而定"的模糊描述。

---

## 一、通用字段约定

### 1.1 每张表必须包含的字段

```sql
-- 所有业务表统一包含以下字段
id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
deleted     TINYINT         NOT NULL DEFAULT 0   -- 逻辑删除: 0=正常, 1=已删除
```

### 1.2 字段类型选型标准

| 场景 | 类型 | 理由 |
|---|---|---|
| 主键/外键 | `BIGINT UNSIGNED` | 统一用 8 字节。50 人场景 `INT` 够用（21 亿），但 `BIGINT` 和 MyBatis-Plus Long 匹配，不用多写类型转换 |
| 短文本（256 字符以内） | `VARCHAR(N)` | 精确控制长度，索引友好 |
| 长文本（无长度限制） | `TEXT` / `MEDIUMTEXT` | 对话内容、提示词、JSON 配置等 |
| JSON 数据 | `JSON` | MySQL 8.x 原生 JSON，可建虚拟列索引，不要用 TEXT 存 JSON |
| 布尔标记 | `TINYINT` | 不用 `ENUM`（ALTER 困难），不用 `BIT`（MyBatis-Plus 映射麻烦） |
| 金额 | `DECIMAL(12,2)` | 不用 FLOAT/DOUBLE，避免精度问题 |
| 百分比/比率 | `DECIMAL(5,4)` | 如 0.85 表示 85%，精确到万分之一 |
| 日期时间 | `DATETIME` | 不用 TIMESTAMP（2038 年溢出风险），不用 DATE 存时间 |

### 1.3 NULL 约束

```sql
-- 强制规则：有默认值的字段 NOT NULL，真正不确定的才允许 NULL
-- ✅ 正确
status      TINYINT  NOT NULL DEFAULT 0
description TEXT     NULL                    -- 描述可以为空，NULL 表示"未填写"
avatar_url  VARCHAR(500) NOT NULL DEFAULT '' -- 头像 URL，没设置就是空字符串

-- ❌ 错误
status      TINYINT  NULL DEFAULT 0         -- status 凭什么 NULL？要么 0 要么 1
name        VARCHAR(100) NULL               -- 名称必须要有，应该是 NOT NULL
```

**判断标准：** 如果你在业务代码里写过 `if (xxx.getStatus() != null)`，说明这个字段不该设 NULL，用 NOT NULL + 默认值。

### 1.4 字符集

```sql
-- 所有表和字段统一使用
CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
-- MySQL 8.x 默认已是 utf8mb4，但建表时显式声明，防止迁移到不同配置的实例时出问题
```

### 1.5 外键

```
规则：一期不建物理外键（FOREIGN KEY），用应用层保证引用完整性。

理由:
  1. 逻辑删除（deleted=0）下，物理外键无法约束"已删除的记录不参与关联"
  2. 大表数据清理时外键检查拖慢 DELETE 性能
  3. 未来如果拆微服务，物理外键是迁移的绊脚石

替代方案:
  1. 关联字段命名统一: 关联 agent 表用 agent_id，关联 conversation 表用 conversation_id
  2. Service 层写数据时校验引用存在性
  3. 对重要的引用关系加备注注释
```

---

## 二、索引设计原则

### 2.1 必须建索引的场景

按优先级从高到低：

| 优先级 | 场景 | 示例 | 索引写法 |
|---|---|---|---|
| P0 | 所有 `WHERE` 条件中高频出现的单列 | `WHERE conversation_id = ?` → 这是 messages 表最频繁的查询 | `INDEX idx_xxx (column)` |
| P0 | 所有 `ORDER BY` 列 | `ORDER BY created_at DESC` → 分页查询必用 | `INDEX idx_xxx (column)` |
| P1 | 联合查询的关联列 | `WHERE conversation_id = ? AND deleted = 0 ORDER BY created_at DESC` | `INDEX idx_conv_del_time (conversation_id, deleted, created_at)` |
| P1 | 逻辑删除 + 业务列组合 | `WHERE deleted = 0 AND user_id = ?` → 几乎所有查询都带 deleted | 联合索引中 deleted 放第一位或第二位 |
| P2 | 唯一约束字段 | `WHERE name = ? AND deleted = 0` → 需保证未删除记录的唯一性 | `UNIQUE INDEX idx_name_del (name, deleted)` |

### 2.2 联合索引顺序规则

```sql
-- 规则：等值条件在前，范围条件在后，排序字段放最后

-- 场景: 查某个对话的消息列表，按时间排序
-- WHERE conversation_id = 123 AND deleted = 0 ORDER BY created_at DESC
CREATE INDEX idx_messages_query
    ON messages (conversation_id, deleted, created_at);
--              ↑ 等值          ↑ 等值    ↑ 排序

-- 为什么这个顺序:
--   1. conversation_id = 等值，放最前，精确定位到一棵 B+Tree 子树
--   2. deleted = 等值，接着过滤，不破坏索引有序性
--   3. created_at = 排序，索引已经有序，MySQL 不需要 filesort
```

### 2.3 禁止行为

| 规则 | 原因 |
|---|---|
| 禁止在索引列上使用函数 | `WHERE DATE(created_at) = '2026-01-01'` 不走索引。应写 `WHERE created_at >= '2026-01-01' AND created_at < '2026-01-02'` |
| 禁止前缀模糊查询 | `WHERE name LIKE '%keyword%'` 不走索引。用全文索引（`FULLTEXT`）或 Elasticsearch 替代 |
| 禁止 `SELECT *` 上有索引但不用 | 回表代价大，索引覆盖不到就全表扫。大表查询必须显式指定列 |
| 禁止对大表直接 `COUNT(*)` | `COUNT(*)` 在大表上走全表。业务统计用近似值、缓存或异步计算 |
| 禁止单表索引超过 5 个 | 每个索引拖慢 INSERT/UPDATE。超过 5 个说明该拆表或改用 ES |

### 2.4 索引命名规范

```sql
-- 普通索引
INDEX idx_{表名缩写}_{列名1}_{列名2}
-- 示例: INDEX idx_msg_conv_deleted (conversation_id, deleted)

-- 唯一索引
UNIQUE INDEX uk_{表名缩写}_{列名}
-- 示例: UNIQUE INDEX uk_provider_name_deleted (name, deleted)

-- 全文索引
FULLTEXT INDEX ft_{表名缩写}_{列名}
-- 示例: FULLTEXT INDEX ft_doc_content (content)
```

### 2.5 EXPLAIN 检查清单

建完索引后，对每个高频查询执行 `EXPLAIN`，检查：

```
□ type 列: 必须是 const / eq_ref / ref / range，不能是 ALL（全表扫描）、index（全索引扫描）
□ key 列: 必须显示索引名，不能是 NULL
□ rows 列: 扫描行数应在百级以内，不能到万级以上
□ Extra 列: 不能出现 Using filesort（排序没走索引）、Using temporary（临时表）
            如果出现 Using where 配合 rows 很少 → 正常
```

---

## 三、大表预判与应对策略

### 3.1 Hify 的数据增长分级

```
表名                    单条大小       增长速率              一年后预估        大表等级
─────────────────────────────────────────────────────────────────────────────
messages                0.5-2 KB       每次对话 2-10 条       50 万-200 万     🔴 大表
document_vectors        2-6 KB         每个文档 N 条分段      10 万-50 万      🟡 中表
workflow_executions     0.5-1 KB       每次工作流执行 1 条    1 万-5 万        🟢 小表
conversations           0.2-0.5 KB     每次对话 1 条          1 万-10 万      🟢 小表
providers               < 1 KB         配置型，不增长          10-20 条         🟢 微型
agents                  < 2 KB         配置型，慢增长          50-200 条        🟢 微型
documents               1 KB           每个文档 1 条          500-5000 条      🟢 小表
```

**🔴 只有 `messages` 是一期就需要关注的大表，其他都不需要特别处理。**

### 3.2 messages 表设计

```sql
CREATE TABLE messages (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id   BIGINT UNSIGNED NOT NULL,
    role              VARCHAR(20)     NOT NULL COMMENT 'user/assistant/system/tool',
    content           TEXT            NULL     COMMENT '消息正文，tool 角色时存工具返回结果',
    tool_calls        JSON            NULL     COMMENT 'assistant 调用工具的 JSON',
    tool_call_id      VARCHAR(100)    NULL     COMMENT 'tool 角色关联的调用 ID',
    token_count       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Token 消耗统计',
    metadata          JSON            NULL     COMMENT '扩展元数据',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT         NOT NULL DEFAULT 0,

    -- 核心查询索引：按对话查消息
    INDEX idx_msg_conv_del_time (conversation_id, deleted, created_at),

    -- 辅助索引：按时间范围查（运维/统计场景）
    INDEX idx_msg_time (created_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='对话消息';
```

**为什么 messages 不需要分区：**

- 一年 200 万行，MySQL 单表轻松扛（InnoDB 在千万行以内无感知）
- `conversation_id` 索引下，每次查询只扫某个对话的几十条消息，B+Tree 深度不会超过 3 层
- 分区表需要维护分区策略，带来的运维复杂度远超性能收益
- 真正需要分区/分表的临界点是 **单表超过 2000 万行或单表超过 50GB**——以当前的增速，要 10 年才到

### 3.3 document_vectors 表（pgvector 端）

```sql
CREATE TABLE document_vectors (
    id              BIGSERIAL PRIMARY KEY,
    knowledge_id    BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL,
    content         TEXT,
    embedding       vector(1536) NOT NULL,  -- OpenAI ada-002 维度
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 向量检索索引（一期用 IVFFlat，数据量到 10 万后切 HNSW）
CREATE INDEX idx_dv_embedding_ivf
    ON document_vectors
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 按知识库查询文档的普通索引
CREATE INDEX idx_dv_knowledge
    ON document_vectors (knowledge_id, document_id);
```

**索引切换时机：**

```sql
-- 当 document_vectors 表超过 5 万行 且 P99 检索延迟 > 200ms 时执行:
DROP INDEX idx_dv_embedding_ivf;
CREATE INDEX idx_dv_embedding_hnsw
    ON document_vectors
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
```

### 3.4 大表数据归档策略（当下不实现，保留设计）

```
何时触发归档:
  messages 超过 500 万行 或 磁盘占用超过 10GB

归档方案:
  1. 创建 messages_archive_2026 表（按月分区）
  2. 夜间定时任务: INSERT INTO archive SELECT * FROM messages
     WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
     AND deleted = 1  -- 只归档已删除的
  3. 从主表 DELETE 已归档数据（分批删，每次 1000 行，不锁表）

当下只需做到:
  - messages 在 Service 层打点记录每日写入量
  - 磁盘使用率监控（K8s Prometheus + node-exporter 自带）
```

---

## 四、分页查询规范

### 4.1 禁止使用 OFFSET 深分页

```sql
-- ❌ 禁止: OFFSET 大量偏移
SELECT * FROM messages
WHERE conversation_id = 123 AND deleted = 0
ORDER BY created_at DESC
LIMIT 20 OFFSET 10000;  -- 需要扫描并丢弃前 10000 行，越往后越慢
```

### 4.2 游标分页（推荐）

```sql
-- ✅ 正确: 游标分页（cursor-based pagination）
-- 第一页
SELECT id, role, content, created_at
FROM messages
WHERE conversation_id = 123
  AND deleted = 0
  AND id < 2147483647        -- 初始游标值 = Long.MAX_VALUE
ORDER BY id DESC
LIMIT 20;

-- 第二页（取上次返回的最后一条的 id 作为游标）
SELECT id, role, content, created_at
FROM messages
WHERE conversation_id = 123
  AND deleted = 0
  AND id < 10456              -- 上一页最后一条的 id
ORDER BY id DESC
LIMIT 20;
```

```java
// Service 层统一的分页封装
@Data
public class CursorPage<T> {
    private List<T> items;
    private Long nextCursor;    // 下一页游标，null 表示到最后一页了
    private Boolean hasMore;
}

// Mapper 统一写法
@Select("""
    SELECT id, role, content, created_at
    FROM messages
    WHERE conversation_id = #{conversationId}
      AND deleted = 0
      AND id < #{cursor}
    ORDER BY id DESC
    LIMIT #{limit}
""")
List<MessageEntity> selectByCursor(
    @Param("conversationId") Long conversationId,
    @Param("cursor") Long cursor,
    @Param("limit") int limit
);
```

### 4.3 总条数的处理

```
规则: 不查精确总数，查"是否有下一页"就够了。

实现: 每次查 LIMIT N+1 条，如果实际返回 N+1 条，说明还有下一页。
      不需要 SELECT COUNT(*)。

示例: 用户要第 2 页（每页 20 条）
      → 查 LIMIT 21，返回 21 条
      → 前端渲染前 20 条
      → hasMore = true
      → 前端显示"加载更多"而不是"共 18357 条，第 2/918 页"
```

### 4.4 允许 OFFSET 的场景

仅当 **表行数不超过 1000 且不会持续增长** 时允许 OFFSET：

```sql
-- ✅ 允许：providers 表最多 50 行
SELECT * FROM providers WHERE deleted = 0 ORDER BY created_at DESC LIMIT 10 OFFSET 0;

-- ✅ 允许：agents 表最多几百行
SELECT * FROM agents WHERE deleted = 0 ORDER BY updated_at DESC LIMIT 20 OFFSET 0;
```

### 4.5 分页用 MyBatis-Plus 的注意事项

```java
// MyBatis-Plus 的 Page<T> 默认用 COUNT 查总数
// 对大表这是性能陷阱

// ❌ 不要在大表上用默认分页
Page<MessageEntity> page = new Page<>(pageNum, pageSize);
messageMapper.selectPage(page, wrapper);  // 会执行 SELECT COUNT(*)

// ✅ 大表用游标分页，只在配置表用 MyBatis-Plus 分页
Page<ProviderEntity> page = new Page<>(pageNum, pageSize, false); // false = 不查 count
providerMapper.selectPage(page, wrapper);
```

---

## 五、SQL 编写规范

### 5.1 必须遵守

| 规则 | 示例 |
|---|---|
| 查询必须带 `deleted = 0` | `WHERE id = ? AND deleted = 0`，不在 MyBatis-Plus 里配全局过滤（容易忘记加导致漏数据） |
| 批量操作每批不超过 1000 条 | `DELETE FROM ... WHERE ... LIMIT 1000`，循环执行 |
| IN 列表不超过 200 个值 | 超过 200 改用临时表 JOIN |
| LIKE 只用右模糊 | `WHERE name LIKE 'keyword%'` 能走索引，`'%keyword%'` 不能 |
| 禁止在 WHERE 里做类型转换 | `WHERE id = '123'` 当 id 是 BIGINT 时，MySQL 会把所有行的 id 转成字符串再比较，类型转换导致索引失效 |

### 5.2 SQL 必须在 Mapper 层

```java
// ✅ 在 Mapper.java 或 Mapper.xml 中定义
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
    @Select("SELECT id, role, content FROM messages WHERE conversation_id = #{convId} AND deleted = 0")
    List<MessageEntity> selectByConversationId(@Param("convId") Long conversationId);
}

// ❌ 禁止在 Service 中用 LambdaQueryWrapper 拼接复杂 SQL
// 简单等值查询可以用 Wrapper，但超过 3 个条件必须写 SQL
```

---

## 六、AI 建表执行模板

当创建新表时，按以下模板填充：

```sql
CREATE TABLE {table_name} (
    -- ===== 必须字段 =====
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    -- {业务字段}
    -- ...
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=已删除',

    -- ===== 索引 =====
    -- 规则 1: WHERE 列建索引
    INDEX idx_{缩写}_{where列} ({where列}),
    -- 规则 2: ORDER BY 列加入联合索引
    -- 规则 3: 等值条件在前，范围条件/排序在后

    -- ===== 唯一索引 =====
    -- UNIQUE INDEX uk_{缩写}_{列} ({列}, deleted)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{表说明}';
```

### 示例：conversations 表

```sql
CREATE TABLE conversations (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    agent_id        BIGINT UNSIGNED NOT NULL COMMENT '使用的 Agent',
    title           VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '对话标题（首条消息摘要）',
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态: 0=进行中, 1=已完成, 2=异常中断',
    message_count   INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '消息数量（冗余，减少 COUNT）',
    total_tokens    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '累计 Token 消耗',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常, 1=已删除',

    -- 用户查自己的对话列表
    INDEX idx_conv_user_del_time (user_id, deleted, updated_at),
    -- Agent 查关联的对话
    INDEX idx_conv_agent_del (agent_id, deleted)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话';
```

### 示例：document_vectors 表（PostgreSQL/pgvector）

```sql
CREATE TABLE document_vectors (
    id              BIGSERIAL PRIMARY KEY,
    knowledge_id    BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    chunk_index     INT          NOT NULL,
    content         TEXT,
    embedding       vector(1536) NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE document_vectors IS '文档向量存储';

-- 向量检索索引
CREATE INDEX idx_dv_embedding
    ON document_vectors
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- 按知识库+文档查询
CREATE INDEX idx_dv_knowledge_doc
    ON document_vectors (knowledge_id, document_id);
```

---

## 七、MyBatis-Plus 配合规范

```yaml
# application.yml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  global-config:
    db-config:
      logic-delete-field: deleted      # 全局逻辑删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    map-underscore-to-camel-case: true
    default-statement-timeout: 15      # SQL 执行超时 15 秒
```

```java
// Entity 模板
@Data
@TableName("conversations")
public class ConversationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long agentId;
    private String title;
    private Integer status;
    private Integer messageCount;
    private Integer totalTokens;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## 八、检查清单（AI 建表时逐项核对）

```
□ 所有字段是否有 COMMENT？
□ 字符集是否 utf8mb4？
□ 引擎是否 InnoDB？
□ id 是否 BIGINT UNSIGNED AUTO_INCREMENT？
□ 是否包含 created_at / updated_at / deleted 三个通用字段？
□ deleted 是否 TINYINT NOT NULL DEFAULT 0？
□ 高频 WHERE 列是否建了索引？
□ ORDER BY 列是否包含在联合索引中？
□ 联合索引的顺序是否是「等值 → 等值 → 排序」？
□ 唯一约束是否考虑了 deleted 字段（未删除记录的唯一性）？
□ 大表（预估 > 10 万行/年）是否避免使用 OFFSET 分页？
□ 大表是否在 Service 层预留了游标分页入口？
□ JSON 字段是否用了 JSON 类型而非 TEXT？
□ 金额类是否用了 DECIMAL 而非 FLOAT？
□ 是否没有创建物理外键？
□ 索引数量是否 ≤ 5？
```

---

> **最后一条：在本项目中，如果对某条规则判断"应该不适用"——不要自己判断。把表结构和预计查询模式写出来，按模板执行。踩过的坑都在上面了。**
