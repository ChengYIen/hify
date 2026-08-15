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

---

## 十九、系统分析：核心链路 / 风险集中区 / 测试重心

> 本节基于当前代码梳理，新增模块或改动上述链路后应同步更新。

### 19.1 核心链路清单

**1. 客服对话 + MCP 工具多轮调用**
- 模块/类：`hify-module-conversation` ChatServiceImpl（sendMessage/doStream/streamWithTools/executeToolCall）；`hify-module-agent` AgentConfigApiImpl + AgentServiceImpl；`hify-module-mcp` McpToolQueryApiImpl、ToolExecutionApiImpl、McpClientServiceImpl、DefaultMcpClientFactory；`hify-module-provider` LlmProviderServiceImpl；`hify-shared` LlmProviderApi/ToolExecutionApi。
- 为什么核心：用户唯一直接触达的主链路，退款场景的 check->submit 多轮工具调用依赖它；这里出问题会表现为对话挂起、工具漏调或重复调用，影响最大。

**2. MCP Server 接入与工具同步**
- 模块/类：`hify-module-mcp` McpServerController、McpServerServiceImpl、McpConnectivityServiceImpl（testConnection/replaceTools）、McpClientServiceImpl.listToolResponses、McpToolMapper.restoreAndUpdate。
- 为什么核心：所有外部工具的可用性都来自这条链路；注册/测试失败或工具表不同步，Agent 绑定和调试页会同时失真。

**3. 工具调试链路**
- 模块/类：`hify-module-mcp` McpDebugServiceImpl -> McpClientServiceImpl.callTool；`hify-web` McpToolDebug.vue。
- 为什么核心：上线前验证工具行为的主要入口，失败时要能区分是远端 Server、工具名还是参数问题。

**4. 工作流执行链路**
- 模块/类：`hify-module-workflow` WorkflowEngine.execute、NodeExecutorRegistry、LlmNodeExecutor、ApiCallNodeExecutor、KnowledgeNodeExecutor、ConditionNodeExecutor；`hify-module-conversation` ChatServiceImpl.streamWorkflowResult。
- 为什么核心：把 LLM、RAG、外部 API 编排成自动化任务，步数上限、循环边和节点超时直接决定任务能否收敛。

**5. 知识库 RAG 检索链路**
- 模块/类：`hify-module-knowledge` KnowledgeDocumentServiceImpl（异步索引）、EmbeddingService、RagRetrievalApiImpl、ChunkVectorRepository；`hify-module-conversation` ChatServiceImpl.buildHistoryMessages；PostgreSQL pgvector。
- 为什么核心：客服回答质量和 token 成本由它决定；索引滞后会给出过时答案，检索失败会静默降级为无知识回答。

### 19.2 风险集中区域

**1. ChatServiceImpl.streamWithTools 工具循环（性能/资源、并发）**
- 失败场景：`hify.llm.agent.total-timeout: 90s` 只存在于 yml，代码没有执行；每轮 LLM 最多重试 3 次 x 60s 读超时，10 轮上限时单请求可远超 300s。SSE 超时后 `cancelLlmCall` 只对 `llmHandle` 生效，工具循环里的同步 `chat()` 取消不掉，llmExecutor 线程继续被占用；并发一多就耗尽 10/50/100 的池子，CallerRuns 策略还会拖住 Tomcat 请求线程。

**2. Agent/工具启停语义失效（数据一致性、行为正确性）**
- 失败场景：`ChatServiceImpl.buildToolSchemas` 不检查 `AgentConfigDTO.toolsEnabled`，Agent 关闭工具后仍会下发 schema；`McpToolMapper.selectBoundTools` 不校验 `hify_mcp_server.status`，停用 MCP Server 后聊天仍能调用旧工具，与界面的“启用/停用”语义不一致。

**3. AgentServiceImpl.page 缓存 key 固定（数据一致性）**
- 失败场景：`@Cacheable(cacheNames = AGENT, key = "'list'")` 不区分 page/pageSize，首次请求结果会被后续分页复用；列表页在不同页码看到同一页数据，直到 TTL 或写操作 evict。

**4. McpConnectivityServiceImpl.replaceTools 同步（并发、数据一致性）**
- 失败场景：非事务；同 Server 并发 test 时两个线程都判断“无旧工具”并 insert 同名工具，触发唯一键 `uk_mcp_tool_server_name` 冲突，其中一个 test 被误报为连接失败；insert/update 成功但 delete 失败会残留已消失的工具，Agent 绑定工具变成“工具不存在或未绑定”。

**5. 认证、限流与 MCP 远端访问（安全）**
- 失败场景：只有 JWT 认证、无角色授权，任意登录用户可调管理 API；`RateLimiterAspect` 已实现但没有任何 Controller 使用 `@RateLimit`，聊天接口无防刷；`McpDebugRequest.toolName` 不校验是否已同步工具，endpoint 也不校验协议/主机，认证用户可让后端连接任意内网地址（SSRF 面）；`JWT_SECRET` 有可预测的默认值，生产漏配即可伪造 token。

**6. 消息序号与会话上下文并发（并发、数据一致性）**
- 失败场景：`ChatMessageServiceImpl` 用 `messageCount + 1` 生成 seq，同 session 并发发消息会拿到相同 seq 或丢消息；`ChatContextCache.pushMessage` 的 RPUSH/EXPIRE/LTRIM 非原子，并发写入可能超量或顺序错乱。

**7. 知识库异步索引与跨库一致性（数据一致性、性能）**
- 失败场景：`KnowledgeDocumentServiceImpl.saveChunks` 的 delete+batchInsert 在 async 线程非事务执行，失败留下半套 chunk；`KnowledgeServiceImpl.delete` 跨 MySQL 和 PG 两个数据源，无分布式事务，一边成功一边失败会残留脏数据；大 PDF 处理占 asyncExecutor（2/4），多文档上传会排队并长期占用。

**8. WorkflowEngine 同步执行（性能、数据一致性）**
- 失败场景：工作流在 llmExecutor 线程里串行跑整条链，LLM 节点多次调用时长时间占线程；`createRun/completeRun` 是 best-effort，DB 故障时任务照跑但没有审计记录；ConditionNodeExecutor 是简化字符串表达式，引号、contains、大小写边界容易产生错误分支。

### 19.3 测试重心建议

**必须有测试覆盖（P0）**
- `ChatServiceImpl` 工具循环：多轮连续 tool_calls、工具失败回填 LLM、调用未绑定工具、参数/tool_calls JSON 解析失败、轮数上限、SSE 客户端断开取消、RAG 检索失败降级。现有 `ChatServiceImplToolTest` 只覆盖 happy path、工具失败、无工具三条。
- `McpConnectivityServiceImpl`：首次全量插入、重复 test 幂等 upsert、逻辑删除恢复、工具消失后清理、连接失败不清空工具、并发 test 唯一键冲突。
- `McpClientServiceImpl`：`isError=true` 转 BizException、多段 TextContent 拼接、连接超时/拒绝、远端返回空内容。
- Agent 启停与绑定：`toolsEnabled=false` 不下发 schema、server 停用后 listBoundTools 过滤、分页缓存 key 正确性。
- 工作流：现有 `WorkflowEngineTest` 已覆盖线性/条件/失败/步数上限，补 API_CALL 与 LLM 节点异常、条件表达式边界、循环边、节点持久化失败。
- 安全：JwtInterceptor 无 token/过期/伪造、RateLimiterAspect 超限与 Redis 不可用降级、MCP endpoint 协议校验（修复后）。
- 知识库：`embedAll` 返回数量不匹配、Redis 缓存降级、异步索引失败落 FAILED、删除文档/知识库后的向量残留。

**可以先跳过**
- 简单 CRUD 的 Controller 层单测（只是 @Valid + 转发，交给集成测试覆盖）。
- Mapper 的常规 BaseMapper SQL；复杂 join（如 selectBoundTools）用一次真实库集成冒烟即可。
- 前端纯展示/样式验收，只做一次 E2E 截图确认。
- 各家 Provider 响应解析差异，只测主用厂商加 mock 一两个兼容厂商。
- Redis/MySQL/PG 自身的高可用，不属于本代码库单测范围。

---

## 二十、单元测试规范（基于核心链路与风险地图）

> 框架：JUnit 5 + Mockito + MockMvc + AssertJ。本节是第十七章测试规范的细化，优先覆盖第十九章核心链路与风险集中区域。原则：每个风险点至少有一个回归测试，修复 bug 前先补失败用例。

### 20.1 必须写单测的代码

按核心链路优先级排序：

| 优先级 | 链路/风险 | 必测对象 | 最少覆盖场景 |
|---|---|---|---|
| P0 | 客服对话 + MCP 工具多轮调用 | `ChatServiceImpl.streamWithTools/executeToolCall/parseToolCalls/buildHistoryMessages` | 多轮连续 tool_calls；工具失败回填 LLM；调用未绑定工具；参数/tool_calls JSON 解析失败；轮数上限；SSE 客户端断开取消；RAG 检索失败降级 |
| P0 | MCP Server 接入与工具同步 | `McpConnectivityServiceImpl.testConnection/replaceTools`、`McpToolMapper.restoreAndUpdate` | 首次全量插入；重复 test 幂等 upsert；逻辑删除恢复；工具消失后清理；连接失败不清空工具；并发 test 唯一键冲突 |
| P0 | MCP 工具调用 | `McpClientServiceImpl.callTool/listToolResponses/extractText` | `isError=true` 转 BizException；多段 TextContent 拼接；连接超时/拒绝；远端返回空内容 |
| P0 | Agent 绑定与启停 | `AgentServiceImpl.updateTools/create/validateToolIds`、`AgentConfigApiImpl`、`McpToolQueryApiImpl` | 超 10 个工具拒绝；不可用工具拒绝；`toolsEnabled=false` 不下发 schema；server 停用后 listBoundTools 过滤；分页缓存 key 正确性 |
| P0 | 工作流执行 | `WorkflowEngine.execute`、`LlmNodeExecutor`、`ApiCallNodeExecutor`、`KnowledgeNodeExecutor`、`ConditionNodeExecutor` | 线性/条件分支；缺 START/目标节点；步数上限；循环边；节点失败落 FAILED；节点持久化失败仍可执行；条件表达式边界 |
| P0 | 安全与限流 | `JwtInterceptor`、`JwtUtil`、`RateLimiterAspect`、MCP endpoint 入参校验 | 无 token/过期/伪造；限流超限；Redis 不可用降级；endpoint 协议/主机校验（修复后） |
| P1 | 知识库 RAG | `EmbeddingService`、`RagRetrievalApiImpl`、`KnowledgeDocumentServiceImpl` 纯逻辑部分 | `embedAll` 返回数量不匹配；Redis 缓存降级；分块不丢不重；异步索引失败落 FAILED |
| P1 | 上下文与消息 | `ChatContextAssembler`、`ChatContextCache`、`ChatMessageServiceImpl` seq 逻辑 | 轮数/token 预算裁剪；Redis 异常回退 MySQL；同 session 消息 seq 递增 |
| P1 | Provider 解析 | `OpenAiAdapter`、`AnthropicAdapter`、`OllamaAdapter` 的 `parseChatResponse/extractStreamDelta` | 各家标准响应；SSE 多行增量；`[DONE]` 结束；401/429/5xx 错误分类 |
| P1 | 纯函数 | `TokenEstimator`、`NodeConfigParser`、`WorkflowDefinitionParser`、`ConditionNodeExecutor.evaluate` | 空值、边界字符、超长文本、非法 JSON |

### 20.2 不写单测、用集成测试替代

- 简单 CRUD Controller：只做 `@Valid` + 转发，用 MockMvc 集成测试覆盖参数校验和响应结构。
- Mapper/MyBatis SQL：常规 BaseMapper 不测；复杂 join（如 `selectBoundTools`）用 `@MybatisTest` + Testcontainers 或真实库冒烟。
- MCP Streamable HTTP/SSE 握手与传输：用本地 mock MCP Server 做集成测试，不 mock SDK 内部实现。
- LLM HTTP/SSE 真实传输：用 WireMock 或本地 mock server；单测和集成测试都严禁真实调用 LLM API。
- Redis/MySQL/PG 连接、事务和降级：用 Testcontainers 集成测试，不写单测模拟连接池。
- 前端：只做 E2E 和截图验收。
- 三方库本身：Spring Boot、MyBatis-Plus、MCP SDK、pgvector 的行为不测。

### 20.3 测试命名规范

统一使用 `should_[期望结果]_when_[输入条件]`，全部小写蛇形，`when` 前用下划线：

```java
@Test
void should_executeToolAndReturnFinalAnswer_when_llmRequestsToolCall() {}

@Test
void should_feedErrorBackToLlm_when_toolCallFails() {}

@Test
void should_notClearTools_when_connectivityTestFails() {}
```

规则：一个测试只验证一个行为，条件要能区分输入来源；同一方法的多个分支通过用例名区分，不写 `test1/test2`。

### 20.4 测试结构：Given-When-Then

每个测试强制三段注释，结构顺序固定：

```java
@Test
void should_xxx_when_yyy() {
    // Given
    ChatSessionResponse session = ...;
    when(chatSessionService.getById(1L)).thenReturn(session);

    // When
    chatService.sendMessage(1L, "查询订单", 2L);

    // Then
    ArgumentCaptor<LlmRequestDTO> captor = ArgumentCaptor.forClass(LlmRequestDTO.class);
    verify(llmProviderApi, times(2)).chat(captor.capture());
    assertThat(captor.getAllValues().get(1).getMessages())
            .extracting(LlmRequestDTO.Message::getRole)
            .containsExactly("system", "assistant", "tool");
}
```

- Given：准备 mock 桩、fixture、入参。
- When：只调用一个被测 public 方法，不夹带其他行为。
- Then：用 AssertJ 断言结果，用 `verify` 断言交互次数和捕获参数。
- 异步逻辑（如 `asyncExecutor`）在 When 前用 `doAnswer` 让任务同步执行，Then 才能稳定断言。

### 20.5 Mock 使用规范

必须 mock 的外部边界：Mapper、`LlmProviderApi`、`McpClientService`、`RagRetrievalApi`、`RedisTemplate`、线程池、时间。

不 mock 的对象：被测类、纯函数/领域逻辑（`ChatContextAssembler`、`NodeConfigParser`、`ConditionNodeExecutor`、`TokenEstimator`）、DTO 和值对象。

其他规则：

- mock 最小化：stub 数量过多说明被测对象依赖太重，优先拆小而不是继续打桩。
- void 方法用 `doThrow/doAnswer`，不用 `when(...).thenThrow()`。
- 线程池统一用 `doAnswer` 同步执行 `Runnable`，禁止 `Thread.sleep`。
- 不用 `Mockito.mockStatic` 除非被测代码确实静态依赖；可用则改为构造器注入。
- MockitoExtension 自动重置 mock，不在 `@BeforeAll` 共享可变 mock 状态。
- 测试里不 mock 被测类的私有方法，通过 public 行为断言。

### 20.6 断言规范

统一使用 AssertJ，断言必须验证可观察的业务结果：

- 返回值断言字段值，不用 `assertNotNull` 代替内容校验。
- 列表断言用 `extracting(...).containsExactly(...)` 验证顺序和内容。
- 异常断言用 `assertThatThrownBy` 或 `catchThrowable`，同时校验 `ErrorCode` 和 message。
- 交互断言用 `verify(...).times(n)/never()`，需要验证请求内容时用 `ArgumentCaptor`。
- 时间类断言只断言范围或 `>= 0`，禁止精确值。
- 禁止只写“不抛异常”的测试，必须有正向结果断言。

### 20.7 禁止事项

- 单测内真实 HTTP 调用 LLM/MCP/Redis/MySQL/PG。
- 用 `Thread.sleep` 等待异步任务完成。
- 精确时间断言（如 `isEqualTo(1000L)`）。
- 无理由的 `@Disabled/@Ignore`；必须注明 ticket 或原因。
- 反射调用私有方法；需要测试时拆成 package-private 纯函数。
- 恒真断言（`assertTrue(true)`、`assertEquals(1, 1)`）。
- `catch` 吞异常或 `System.out.println` 代替断言。
- 在测试里复制生产逻辑来计算期望值。
- 共享可变静态状态导致用例互相污染。
- 依赖真实自增 ID、数据库顺序或未 seed 的随机数据。
- 修改被测代码只为了让测试通过而不验证真实行为。
