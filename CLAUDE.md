# CLAUDE.md — Hify 项目完整规范

> **项目定位：** Spring Boot 单体应用，AI Agent 对话平台，1 人开发，50 人内部使用。
> **技术栈：** Spring Boot 3 + MyBatis-Plus + Vue 3 + MySQL 8.0 + Redis 7 + PostgreSQL 16 + pgvector + K8s。
> **架构：** 模块化单体（com.hify），6 个业务模块，通过 shared 接口通信。
> **核心原则：** 模块高内聚、跨模块走 shared 契约、LLM 调用必须与请求线程隔离、事务不包裹外部 IO。

---

## 一、项目顶层结构

```
com.hify
├── common                              // 全局基础设施（不放业务逻辑）
│   ├── config                          // Spring 配置类（线程池、Retry、RestClient）
│   ├── exception                       // 全局异常定义 + GlobalExceptionHandler
│   ├── web                             // 统一响应体 R<T>、分页封装
│   └── util                            // 纯工具类
│
├── module                              // 业务模块（6 个）
│   ├── provider                        // 模型提供商管理（OpenAI/Claude/Gemini/Ollama）
│   ├── agent                           // Agent 配置
│   ├── conversation                    // 对话引擎 + Agent 循环
│   ├── knowledge                       // 知识库 RAG
│   ├── workflow                        // 工作流执行
│   └── mcp                             // MCP 工具接入
│
└── shared                              // 模块间共享契约（只有接口 + DTO，没有实现）
    ├── llm                             // LLM 统一调用接口
    ├── tool                            // 工具执行接口
    ├── agent                           // Agent 配置查询接口
    └── rag                             // 检索增强接口
```

### 铁律（违反即 bug）

| 规则 | 说明 |
|---|---|
| `common` 不写业务代码 | 引用了业务 Entity/DTO 的代码不属于 common |
| `shared` 只有接口+POJO | 绝不允许 `@Service`、`@Component`、实现类 |
| `module` 之间不互相 import | module/a 不能 import module/b.XxxService |
| 跨模块通信只通过 `shared` 接口 | 调用方注入接口，提供方实现接口 |

### 模块隔离方式

**Maven 多模块 + 包级隔离。** 每个顶层包（`common`/`shared`/`module/*`）是一个独立 Maven module，有自己的 `pom.xml`。模块间只通过 Maven 依赖 + `shared` 接口通信，编译期即可杜绝非法 import。

| Maven module | 对应包路径 | 依赖关系 |
|---|---|---|
| `hify-common` | `com.hify.common` | 无内部依赖 |
| `hify-shared` | `com.hify.shared` | 无内部依赖 |
| `hify-module-provider` | `com.hify.module.provider` | → `hify-common` + `hify-shared` |
| `hify-module-agent` | `com.hify.module.agent` | → `hify-common` + `hify-shared` |
| `hify-module-conversation` | `com.hify.module.conversation` | → `hify-common` + `hify-shared` |
| `hify-module-knowledge` | `com.hify.module.knowledge` | → `hify-common` + `hify-shared` |
| `hify-module-workflow` | `com.hify.module.workflow` | → `hify-common` + `hify-shared` |
| `hify-module-mcp` | `com.hify.module.mcp` | → `hify-common` + `hify-shared` |
| `hify-app` | `com.hify` | → 组装所有 module（启动入口） |

**模块之间不能互相依赖** —— 编译期由 Maven 强制保证，不会出现 `module/a` import `module/b.XxxService` 的情况。

---

## 二、单模块内部结构

```
module/<name>/
├── controller/
│   ├── XxxController.java              // 只做三件事：@Valid 校验 → 调 Service → 返回 R<T>
│   └── dto/
│       ├── XxxCreateRequest.java       // 入参：创建
│       ├── XxxUpdateRequest.java       // 入参：更新
│       ├── XxxQueryRequest.java        // 入参：查询
│       └── XxxResponse.java            // 出参
│
├── service/
│   ├── XxxService.java                 // 接口
│   └── impl/
│       └── XxxServiceImpl.java         // 实现，事务边界
│
└── repository/
    ├── XxxMapper.java                  // 继承 BaseMapper<XxxEntity>
    └── entity/
        └── XxxEntity.java              // 表 1:1 映射，只有字段+getter/setter
```

> **个别模块可增设子包：** 上述三层是标准结构。`provider` 模块额外需要 `client/` 子包存放各 LLM 厂商的 HTTP 客户端（`OpenAiClient`、`AnthropicClient`、`GeminiClient`、`OllamaClient`），这些客户端只负责构造 HTTP 请求和解析响应，不包含业务逻辑。

### 各层铁律

| 层 | 规则 |
|---|---|
| **Controller** | ≤8 个 public 方法；方法体 ≤15 行；不写业务逻辑；入参用独立 Request 类，不用 Entity；语义有歧义的更新接口要拆开（基本信息 vs 关联数据分开端点） |
| **Service** | `@Transactional(rollbackFor = Exception.class)` 只在 Service 方法上；返回值是 DTO，不返回 Entity；不操作 HttpServletRequest/Response；关联表更新优先全量替换（删旧插新），数据量小（<100 条）时比 diff 更简单可靠 |
| **Repository** | 只做数据访问，不含业务判断；Entity 不出 Service 层；Entity 不写 `validate()`/`activate()` 等业务方法 |
| **跨模块调用** | 调用方只注入 `shared` 接口，走 Service 不直接调 Mapper；shared DTO 不引用任何 module 的类 |

### DTO 命名规范

| 后缀 | 用途 | 位置 |
|---|---|---|
| `*Request` | HTTP 入参 | `controller/dto/` |
| `*Response` | HTTP 出参 | `controller/dto/` |
| `*DTO` | 跨模块/共享层传递 | `shared/<domain>/` |

### 统一响应体

```java
// common/web/R.java  —— 所有 Controller 返回值必须是 R<T>
@Data
public class R<T> {
    private int code;        // 0=成功，非0=错误
    private String message;
    private T data;

    public static <T> R<T> ok(T data) { ... }
    public static <T> R<T> fail(int code, String message) { ... }
}
// 不在 R 上加 @JsonInclude，null 字段也返回
```

---

## 三、命名规范

1. **类名 UpperCamelCase，方法/参数/变量 lowerCamelCase，常量 CONSTANT_CASE。**
   反例：`GetData()`、`user_name`、`MAX_COUNT`（局部变量）。

2. **分层后缀固定：** Controller → `XxxController`；Service 接口 → `XxxService`，实现 → `XxxServiceImpl`；Mapper → `XxxMapper`。

3. **Boolean 变量禁止 `is` 前缀。** POJO 布尔字段用 `Boolean`（包装类），getter 用 `getXxx()`。方法名用 `canXxx()`/`hasXxx()`/`shouldXxx()`。

4. **Service/Mapper 方法命名以动词开头：**
   - 获取单个：`getXxx` / `findXxx`
   - 获取列表：`listXxx` / `queryXxx`
   - 统计：`countXxx`
   - 新增：`saveXxx` / `insertXxx`
   - 删除：`deleteXxx` / `removeXxx`
   - 更新：`updateXxx`

5. **数组声明用 `Type[]`，不用 `Type arr[]`。**

---

## 四、异常处理

6. **业务异常只抛 `BusinessException(ErrorCode)`，禁止 `throw new RuntimeException("xxx")`。**

7. **错误码枚举 `ErrorCode` 分段管理：**
   - 1xxxx = provider，2xxxx = agent，3xxxx = conversation，4xxxx = knowledge，5xxxx = workflow，6xxxx = mcp/LLM

8. **异常处理必须使用 `ErrorCode` 枚举，禁止硬编码错误码和错误信息。** `throw new BizException(ErrorCode.PARAM_INVALID, "具体原因")` 正确，`throw new RuntimeException("参数错误")` 错误。

9. **Service 抛异常，Controller 不 try-catch。** 全部交给 `@RestControllerAdvice GlobalExceptionHandler` 统一转译为 `Result.fail()`。

10. **禁止空 catch 块。** 至少打 log；确实不需要处理时必须注释说明原因。禁止 `catch(Exception)` 一刀切——捕获具体异常类型。

11. **finally 块禁止 return**——会吞掉 try 的返回值和 catch 的异常。

---

## 五、日志规范

12. **使用 Lombok `@Slf4j`，不用 `LoggerFactory.getLogger()`。**

13. **日志级别：**
    - `error`：影响功能、需人工介入
    - `warn`：可恢复异常、降级触发、参数校验不通过
    - `info`：关键业务节点（对话开始/结束、LLM 调用入参出参）
    - `debug`：调试信息，生产默认关闭

14. **禁止字符串拼接，必须用占位符。** `log.info("userId={}, orderId={}", userId, orderId);`

15. **禁止在生产日志中输出：** 大文本（JSON > 10KB）、大对象全量 toString、敏感信息（密码/Token/手机号/身份证/银行卡）。手机号脱敏 `138****0000`。

---

## 六、并发与线程池

16. **线程池必须通过 `ThreadPoolExecutor` 手动创建，禁止 `Executors.newXxx()`。**
    `newCachedThreadPool` 无限线程 → OOM；`newFixedThreadPool` 无限队列 → OOM。

17. **本项目线程池规划（4 个池，各司其职）：**

| 线程池 | 核心/最大 | 队列 | 拒绝策略 | 职责 |
|---|---|---|---|---|
| `llmExecutor` | 8/100 | 0 | CallerRunsPolicy | LLM API 调用（IO 密集型） |
| `workflowExecutor` | 4/20 | 50 | AbortPolicy | 工作流执行 |
| `documentExecutor` | 2/4 | 20 | CallerRunsPolicy | 文档解析（CPU 密集型） |
| `backgroundExecutor` | 2/4 | 500 | DiscardOldestPolicy | 日志记录、用量统计等后台任务 |

18. **LLM 调用必须与 Tomcat 请求线程分离。** Controller 接收请求后，提交到 `llmExecutor` 异步执行，Tomcat 线程立即释放。

19. **`SimpleDateFormat` 禁止定义为 static。** 用 `DateTimeFormatter` 替代。

20. **并发写场景：`HashMap` → `ConcurrentHashMap`，`ArrayList` → `CopyOnWriteArrayList`。**

21. **锁内禁止做 RPC/DB 查询/日志写盘等耗时操作。** 读多写少用 `ReentrantReadWriteLock`。`volatile` 仅保证可见性，复合操作（count++）必须用 `AtomicInteger`。

---

## 七、事务与数据库连接

22. **事务绝不包裹 LLM 调用或任何外部 IO。** 这是本项目最容易犯的致命错误：

```java
// ❌ 致命：事务里调 LLM，连接被持有 30 秒+
@Transactional
public void handleMessage(MessageRequest request) {
    messageMapper.insert(userMessage);       // 拿连接
    LlmResponseDTO response = llmProviderApi.chat(...); // 连接被持有 30s！
    messageMapper.insert(aiMessage);
}

// ✅ 正确：事务只包裹数据库操作，快速拿放
public void handleMessage(MessageRequest request) {
    messageService.saveUserMessage(userMessage);  // 独立事务，毫秒级
    LlmResponseDTO response = llmProviderApi.chat(...); // 不占连接
    messageService.saveAiMessage(aiMessage);  // 独立事务
}
```

23. **HikariCP 配置：** `maximum-pool-size=15`（每 Pod），`connection-timeout=10000`，`leak-detection-threshold=5000`（连接持有超 5s 打警告日志）。

24. **`@Transactional(rollbackFor = Exception.class)` 只标在 Service 方法上**，不标在 Controller 或 Mapper。

---

## 八、数据库设计

25. **每张表必须包含：** `id BIGINT UNSIGNED AUTO_INCREMENT`、`created_at DATETIME`、`updated_at DATETIME`、`deleted TINYINT DEFAULT 0`。

26. **字段类型选型：** 短文本 → `VARCHAR(N)`；长文本 → `TEXT`；JSON → `JSON`（MySQL 原生）；布尔 → `TINYINT`；金额 → `DECIMAL(12,2)`；日期时间 → `DATETIME`。不用 ENUM、BIT、FLOAT/DOUBLE、TIMESTAMP。

27. **字符集统一 `utf8mb4`，引擎统一 `InnoDB`，不建物理外键**（应用层保证引用完整性）。

28. **索引规则：**
    - WHERE 高频列 + ORDER BY 列必建联合索引
    - 联合索引顺序：等值条件在前 → 范围条件 → 排序字段放最后
    - 禁止在索引列上用函数、前缀模糊 `%keyword`、单表超 5 个索引
    - 命名：`idx_{表缩写}_{列名}`，唯一索引用 `uk_` 前缀

29. **分页：大表（预估 >10 万行/年）必须用游标分页，禁止 `LIMIT OFFSET` 深分页。** 小表（<1000 行）可用 MyBatis-Plus Page。查 `LIMIT N+1` 条判断 `hasMore`，不查 `COUNT(*)`。

30. **SQL 只在 Mapper 层。** 复杂查询（>5 行）用 XML，简单查询可用 `@Select`。Service 层禁止拼 SQL。`IN` 列表不超 200 个值，批量操作每批 ≤1000 条。

31. **逻辑删除用 `@TableLogic` + `deleted` 字段。** 查询 SQL 必须带 `deleted = 0`（MyBatis-Plus 自动追加）。

---

## 九、LLM 调用韧性（四件套）

32. **超时分层：** 建立连接 10s → 单次 LLM 读取 60s（Ollama 大模型 180s）→ Agent 循环总 90s → 用户请求 120s。每层超时必须逐层递减。

33. **重试规则：** 最多 3 次，指数退避 1s→2s→4s（+30% 随机抖动）。429（限流）/5xx/Connection Timeout → 重试；401/400 → 不重试直接抛错。

34. **熔断规则：** Resilience4j，每个 provider 独立熔断（OpenAI 挂了不影响 Claude）。只有 ConnectException/IOException 触发熔断，429 和 Read Timeout 不触发。滑动窗口 20 次、失败率 50%、半开等待 30s。

35. **调用链顺序：** `@CircuitBreaker(外层) → @Retryable(内层) → 实际 HTTP 调用`。熔断打开时 Retry 不会执行 → 省去无意义等待。

36. **模型降级：** 主模型重试耗尽后，逐一尝试备选 provider；所有模型都失败 → `ErrorCode.LLM_ALL_MODELS_FAILED`。

---

## 十、配置与环境

37. **多环境配置：** `application.yml`（公共+默认值）→ `application-dev.yml` → `application-prod.yml`。

38. **敏感信息只用环境变量 `${ENV_VAR:default}`，绝不硬编码。** 生产环境密码/密钥通过 K8s Secret 注入。

39. **关键生产配置：**
    - SSE 长连接：Ingress `proxy-read-timeout: 300s`，`proxy-buffering: off`
    - 文件上传限制：`max-file-size: 50MB`
    - JVM：`-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError`
    - 健康检查：暴露 `/actuator/health/readiness`、`/actuator/health/liveness`

---

## 十一、性能红线

40. **文档解析必须用独立线程池 `documentExecutor`**，异步处理+进度回调。一个人上传大 PDF 不能阻塞其他人的对话。

41. **所有线程池加 10 秒定时监控日志：** active/pool/queue/completed 四项指标，出问题能快速定位哪个池子满了。

42. **@Async 使用时启用 `@EnableAsync`**，异步方法返回 `CompletableFuture<T>`，异常用 `.exceptionally()` 兜底。

43. **Redis 降级逻辑：** 缓存不可用时跳过缓存直接调 API，不能因为 Redis 挂了就报 500。

---

## 十二、快速检查清单

### 新增模块时
- [ ] 模块目录含 `controller/` `service/impl/` `repository/entity/` 三层
- [ ] Controller 入参是独立 Request 类，不直接用 Entity
- [ ] Controller 方法 ≤15 行，只做校验+调用+返回
- [ ] Service 方法标注 `@Transactional(rollbackFor = Exception.class)`
- [ ] Service 返回值是 DTO/Response，不返回 Entity
- [ ] Mapper 继承 `BaseMapper<XxxEntity>`
- [ ] Entity 使用 `@TableName` `@TableId` `@TableLogic`

### 跨模块调用时
- [ ] 被调功能在 `shared/` 下已有接口？没有则先定义接口+DTO
- [ ] 调用方只注入 `shared` 接口，不 import 其他 module 的类
- [ ] shared 接口的入参/返回值全是 shared DTO，不引用 module 的类
- [ ] 检查 module 之间没有任何 `import` 依赖

### 代码提交前自查
- [ ] 是否有 `catch (Exception) {}` 空块？
- [ ] 是否有 `Executors.newXxx()` 创建的线程池？
- [ ] 是否有 static `SimpleDateFormat`？
- [ ] 事务方法里是否有 LLM 调用/RPC/HTTP 调用？
- [ ] 日志是否用占位符而非字符串拼接？
- [ ] application.yml 里是否有硬编码密码/密钥？
- [ ] 大表查询是否用了游标分页而非 OFFSET？
- [ ] 敏感字段是否脱敏日志输出？
- [ ] 是否使用了 `@Slf4j` 而非手动创建 Logger？
- [ ] SQL 是否只在 Mapper 层？（搜索 Service 层有没有拼 SQL）
- [ ] 索引列上是否有函数调用或前缀模糊查询？
- [ ] HashMap 并发写是否用了 ConcurrentHashMap？

---

> **最后一条：当觉得"这次情况特殊，违规一下问题不大"时——不要违规。规范就是为了防止三个月后回来看代码时骂自己。**

---

## 十三、PostgreSQL + pgvector 规范

> pgvector 只用于知识库向量检索，业务数据全部走 MySQL。不要混用。

### 13.1 何时用 PostgreSQL

| 场景 | 数据库 | 说明 |
|---|---|---|
| 业务 CRUD（用户/Agent/对话/工作流） | MySQL | 所有业务表 |
| 知识库文档块 + 向量 | PostgreSQL + pgvector | 仅 `knowledge` 模块使用 |
| 向量相似度检索 | PostgreSQL + pgvector | `<=>` 距离运算符 |

### 13.2 向量表约定

```sql
-- 知识库文档块表（PostgreSQL）
CREATE TABLE document_chunk (
    id BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,          -- 对应 MySQL 中 knowledge 表的 ID
    chunk_index INTEGER NOT NULL,          -- 块序号
    content TEXT NOT NULL,                 -- 原文
    embedding VECTOR(1536) NOT NULL,       -- OpenAI text-embedding-ada-002 = 1536 维
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 向量索引（HNSW 性能优于 IVFFlat，建表后创建）
CREATE INDEX idx_knowledge_chunk_embedding
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
```

| 规则 | 说明 |
|---|---|
| 向量维度固定 | 按使用的 embedding 模型固定，切换模型需重建表 |
| HNSW 优于 IVFFlat | 查询性能更好，构建稍慢但可接受 |
| `knowledge_id` 对应 MySQL 业务 ID | PostgreSQL 不存业务元数据，只存向量+原文 |
| 连接池独立 | pgvector 用独立 HikariCP，不跟 MySQL 共享 |

### 13.3 PostgreSQL 连接池

```yaml
# application.yml
spring:
  datasource:
    postgresql:
      jdbc-url: jdbc:postgresql://${PG_HOST:localhost}:5432/hify
      username: ${PG_USER:hify}
      password: ${PG_PASSWORD:}
      maximum-pool-size: 5        # 50 人场景够用，向量查询耗时不长
      connection-timeout: 10000
```

---

## 十四、Redis 缓存规范

### 14.1 Key 命名

```
hify:{module}:{entity}:{identifier}
```

示例：
- `hify:provider:list:all` — 所有启用的 provider 列表
- `hify:agent:config:123` — Agent ID=123 的配置
- `hify:conversation:session:abc-def` — 对话 session
- `hify:workflow:status:456` — 工作流执行状态

| 规则 | 说明 |
|---|---|
| 统一前缀 `hify:` | 避免与其他应用共用 Redis 时的 key 冲突 |
| 冒号分层 | `hify:模块:实体:标识` 结构清晰，方便 Redis 客户端按模式浏览 |
| 禁止无 TTL | 每个 key 必须设过期时间，不能让 Redis 内存无限增长 |

### 14.2 TTL 策略

| 数据类型 | TTL | 理由 |
|---|---|---|
| LLM 调用结果缓存 | 5 min | 同一问题短期内可能再问 |
| Provider/ApiKey 配置 | 10 min | 变更频率低，变更后主动失效 |
| Agent 配置 | 10 min | 同上 |
| 对话 session 状态 | 30 min | 对话中断后可恢复 |
| 工作流执行状态 | 1 hour | 执行完成后保留一段可查 |
| 限流计数器 | 1 min（滑动窗口） | 短期统计 |
| 临时数据（验证码等） | 5 min | 用完即弃 |

### 14.3 序列化

```java
// RedisConfig.java
@Bean
public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);

    // Key 用 String 序列化
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    // Value 用 JSON 序列化（Jackson）
    Jackson2JsonRedisSerializer<Object> jsonSerializer =
        new Jackson2JsonRedisSerializer<>(Object.class);
    template.setValueSerializer(jsonSerializer);
    template.setHashValueSerializer(jsonSerializer);

    return template;
}
```

### 14.4 缓存策略

| 场景 | 策略 | 说明 |
|---|---|---|
| 读多写少（配置类） | Cache-Aside | 先查缓存 → 未命中则查库 → 写缓存 |
| 写操作 | 删缓存，不更新 | 下次读时重建，避免双写不一致 |
| 对话状态 | 直接读写 Redis | 对话状态变化频繁，不经过 MySQL |
| 降级 | 缓存不可用时跳过 | Redis 挂了直接调 API，绝不因此报 500 |

---

## 十五、认证方案

### 15.1 MVP 阶段：简易 JWT

```java
// 一期不做 Spring Security，手写一个轻量拦截器
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 解析 JWT → 写入 ThreadLocal 或 request attribute
        Long userId = JwtUtil.parseUserId(token.substring(7));
        UserContext.setCurrentUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(...) {
        UserContext.clear(); // 防止内存泄漏
    }
}
```

| 规则 | 说明 |
|---|---|
| 一期用拦截器，不用 Spring Security | 50 人内部工具，无需 RBAC，一个拦截器够了 |
| JWT 有效期 7 天 | 内部工具不需要频繁登录 |
| 用户表从简 | `id, username, password(bcrypt), display_name, role, created_at` |
| `UserContext` 用 ThreadLocal | 请求链路内随时可取当前用户 ID |
| API 路径 `/api/v1/public/**` 放行 | 登录接口不走拦截器 |

### 15.2 后续升级路径

当需要角色权限（管理员/普通用户）时，引入 Spring Security + `@PreAuthorize`，但 MVP 先不碰。

---

## 十六、前端结构（Vue 3）

```
hify-frontend/
├── src/
│   ├── api/                          // API 封装（一个模块一个文件）
│   │   ├── request.js                // axios 实例 + 拦截器
│   │   ├── provider.js               // 模型提供商 API
│   │   ├── agent.js                  // Agent API
│   │   ├── conversation.js           // 对话 API（含 SSE 流式）
│   │   ├── knowledge.js              // 知识库 API
│   │   ├── workflow.js               // 工作流 API
│   │   └── mcp.js                    // MCP API
│   │
│   ├── views/                        // 页面（对应后端模块）
│   │   ├── provider/
│   │   ├── agent/
│   │   ├── conversation/
│   │   ├── knowledge/
│   │   ├── workflow/
│   │   └── mcp/
│   │
│   ├── components/                   // 公共组件
│   │   ├── common/                   // Button、Modal、Table 等二次封装
│   │   ├── chat/                     // 对话相关组件（消息气泡、输入框、SSE 流式渲染）
│   │   └── layout/                   // 布局组件
│   │
│   ├── composables/                  // 组合式函数（Vue 3 Composition API）
│   │   ├── useSSE.js                 // SSE 流式接收 Hook
│   │   ├── useAuth.js                // 认证 Hook
│   │   └── usePagination.js          // 游标分页 Hook
│   │
│   ├── stores/                       // Pinia 状态管理
│   │   ├── user.js                   // 当前用户信息
│   │   └── app.js                    // 全局 UI 状态（侧边栏、主题）
│   │
│   └── router/                       // Vue Router 路由配置
│       └── index.js
│
└── vite.config.js
```

| 规则 | 说明 |
|---|---|
| API 层与后端模块一一对应 | 每个 `module/<name>` 对应 `api/<name>.js` |
| 不写 TypeScript | MVP 阶段原生 JS + JSDoc，不增加类型系统复杂度 |
| 组件库用 Element Plus | 阿里系内部常用，文档中文友好 |
| SSE 流式渲染用 `useSSE` composable | 封装 `EventSource` / fetch reader，不散落在组件里 |
| 状态管理只放全局共享数据 | 页面内部数据用组件自身的 `ref`/`reactive`，不放 store |

---

## 十七、测试规范

### 17.1 目录结构

```
src/test/java/com/hify/
├── module/
│   ├── provider/
│   │   ├── controller/              // Controller 集成测试（MockMvc）
│   │   └── service/                 // Service 单元测试（JUnit 5 + Mockito）
│   ├── agent/
│   │   └── ...
│   └── ...
└── common/
    └── util/                        // 工具类单元测试
```

### 17.2 框架与约定

| 规则 | 说明 |
|---|---|
| 测试框架 | JUnit 5 + Mockito + MockMvc |
| Service 层写单元测试 | Mock 掉 Mapper 和外部依赖，验证业务逻辑 |
| Controller 层写集成测试 | MockMvc 模拟 HTTP 请求，验证参数校验和响应格式 |
| 类命名 | `XxxServiceTest` / `XxxControllerTest` |
| 方法命名 | `should_xxx_when_yyy` |
| LLM 调用必须 Mock | 测试中绝不允许真实 HTTP 调 LLM API |
| 断言库 | AssertJ（流式断言，可读性好） |

### 17.3 覆盖率目标

| 层级 | MVP 目标 | 说明 |
|---|---|---|
| Service 核心逻辑 | ≥60% | 事务性业务（创建/更新/删除）必须有测试 |
| Controller 参数校验 | ≥80% | 校验注解多，容易漏，必须覆盖 |
| 工具类 | ≥80% | JwtUtil、加密工具等纯函数 |
| Mapper | 不强制 | MyBatis-Plus BaseMapper 不需要测 |

---

## 十八、参考文档

以下设计文档是 CLAUDE.md 规则的详细展开，包含完整代码示例：

| 文档 | 内容 |
|---|---|
| [code-organization-spec.md](docs/code-organization-spec.md) | 目录结构、DTO 规范、跨模块调用完整示例 |
| [llm-resilience-design.md](docs/llm-resilience-design.md) | 线程池隔离、超时/重试/熔断完整实现代码 |
| [database-performance-spec.md](docs/database-performance-spec.md) | MySQL 索引、分页、批量操作细则 |
| [deployment-architecture.md](docs/deployment-architecture.md) | K8s Pod 规格、健康检查、Ingress 配置 |
| [performance-bottleneck-analysis.md](docs/performance-bottleneck-analysis.md) | 已知瓶颈及处理优先级 |
