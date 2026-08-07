# Hify 业务模块交付流程

> **适用场景：** 从零交付一个 Hify 业务模块（如 provider、agent、conversation）。
> **核心理念：** 自底向上分层构建，每层独立验证，不跨层跳跃。
> **参考范例：** Provider 模块（模型提供商管理）是第一个完整交付的模块，本文档以其为蓝本。

---

## 阶段总览

```
Phase 0: 咨询设计（数据模型 + 边界场景）
    ↓  产出: 数据模型文档 + schema.sql 变更清单
Phase 1: 数据模型落地（schema.sql + Entity + Mapper）
    ↓  产出: 表结构 + 实体类 + Mapper 接口
Phase 2: DTO 层（入参 Request + 出参 Response + ConnectionTestResult 等业务对象）
    ↓  产出: controller/dto/ 下的所有 POJO
Phase 3: Service 层（接口 + 实现）
    ↓  产出: Service 接口 + ServiceImpl
Phase 4: Controller 层（REST 端点）
    ↓  产出: Controller + @Valid 校验
Phase 5: 前端对接（API 文件 + 页面组件）
    ↓  产出: api/*.ts + views/*.vue
Phase 6: 完整验收（后端 curl + 浏览器全流程）
    ↓  产出: 验收报告
```

**每一步完成后再进下一步。** 不要跨层跳跃——比如 Service 没写完就去写 Controller，或者 DTO 没定义就去写 Service。

---

## Phase 0: 咨询设计

### 0.1 供应商/技术选型（仅首个模块或引入新依赖时）

**产出物：** 选型对比表

| 维度 | 需覆盖的问题 |
|------|-------------|
| API 协议 | REST？gRPC？SSE？各厂商 API 差异点是什么？ |
| 认证方式 | Bearer Token？x-api-key？OAuth2？是否需要加密存储？ |
| 数据模型 | 几张表？JSON 字段还是关联表？枚举值有哪些？ |
| 边界场景 | 多实例（同厂商不同 Key）怎么处理？API Key 过期怎么办？ |

> **🔴 等待用户确认：** 供应商选型结论、数据模型设计、边界场景处理策略。

### 0.2 数据模型设计

**产出物：** ER 草图 + 字段清单

对本模块涉及的每张表，逐字段过一遍：
- 字段名、类型、是否必填、默认值
- JSON 列的结构（如 Provider 的 `auth_config`）
- 枚举值的完整取值空间（如 `health_status`: HEALTHY / UNHEALTHY / DEGRADED / UNKNOWN）
- 索引需求（WHERE 条件 + ORDER BY 字段）

> **🔴 等待用户确认：** 表结构、JSON 列结构、枚举值定义。

### 0.3 边界问题确认

常见边界场景（以 Provider 为例）：

| 场景 | 处理策略 |
|------|---------|
| 同一 provider_code 多实例 | 允许，用 `name` 字段区分 |
| API Key 泄露 | `auth_config` JSON 加密存储，API 返回不包含明文 |
| 健康检查失败 | 连续 3 次失败 → UNHEALTHY，每次检查写 `provider_health` 历史 |
| 模型发现 | 支持 AUTO（调 API 同步）和 MANUAL（手动维护）两种模式 |
| 连通性测试 | 纯 IO 操作，不纳入事务；超时 10s |

> **🔴 等待用户确认：** 每个边界场景的处理策略。

### 0.4 接口清单

在写代码前，先列出本模块需要暴露的全部 REST 端点，与用户对齐后再动手：

```
GET    /api/v1/{resources}              分页列表（?page=1&pageSize=20&status=xxx）
GET    /api/v1/{resources}/{id}         详情
POST   /api/v1/{resources}              创建
PUT    /api/v1/{resources}/{id}         更新
DELETE /api/v1/{resources}/{id}         删除
POST   /api/v1/{resources}/{id}/...     模块特有操作（如连通性测试）
```

> **🔴 等待用户确认：** 端点路径、查询参数、特有操作的设计。

---

## Phase 1: 数据模型落地

### 1.1 更新 schema.sql

**文件位置：** `hify-app/src/main/resources/db/schema.sql`

**产出物：** 完整的 `CREATE TABLE` 语句，包含：
- 所有字段 + 类型 + 默认值 + 注释
- 标准字段 `id BIGINT UNSIGNED AUTO_INCREMENT`、`created_at`、`updated_at`、`deleted TINYINT DEFAULT 0`
- 索引（命名：`idx_{表缩写}_{列名}`，唯一索引 `uk_` 前缀）
- 引擎 `InnoDB`，字符集 `utf8mb4`

**验证方式：** 对照数据模型文档逐字段检查。

### 1.2 创建 schema-h2.sql（H2 兼容版本）

**文件位置：** `hify-app/src/main/resources/db/schema-h2.sql`

**为什么需要：** mock profile 使用 H2 内存数据库，方便无 MySQL/PostgreSQL 环境快速启动。H2 不兼容部分 MySQL 语法，必须维护独立 DDL。

**与 schema.sql 的关键差异：**

| MySQL | H2 |
|-------|----|
| `JSON` | `CLOB` |
| `TINYINT` | `SMALLINT` |
| `BIGINT UNSIGNED AUTO_INCREMENT` | `BIGINT AUTO_INCREMENT` |
| `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | 去掉 |
| `ON UPDATE CURRENT_TIMESTAMP` | 去掉或放在业务层处理 |

**验证：**
```bash
# 用 mock profile 启动，H2 自动执行 schema-h2.sql
mvn spring-boot:run -pl hify-app -Dspring-boot.run.profiles=mock
# 访问 H2 控制台确认表已创建
# http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:hify)
```

> **⚠️ 注意事项：** 每次改 `schema.sql` 必须同步改 `schema-h2.sql`。两个文件字段要一致，差异只在类型语法。

### 1.3 创建 Entity

**文件位置：** `hify-module-{name}/src/main/java/com/hify/module/{name}/repository/entity/XxxEntity.java`

**产出物清单（以 Provider 为例）：**

```
repository/entity/
├── Provider.java          // 主表实体，extends BaseEntity
├── AuthConfig.java        // JSON 列对应的 POJO（auth_config）
├── ModelConfig.java       // JSON 列对应的 POJO（model_configs）
├── ModelExtraParams.java  // JSON 嵌套 POJO
├── ProviderHealth.java    // 健康检查历史表实体
├── HealthStatus.java      // 可选：健康状态常量
├── ModelType.java         // 可选：模型类型常量
└── DiscoveryType.java     // 可选：发现方式常量
```

**关键规范：**

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "hify_provider", autoResultMap = true)  // autoResultMap 必须开
public class Provider extends BaseEntity {

    // JSON 列必须用 JacksonTypeHandler
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AuthConfig authConfig;

    // 枚举值用 String 存储，不用 ENUM
    private String status;         // ENABLED / DISABLED
    private String healthStatus;   // HEALTHY / UNHEALTHY / DEGRADED / UNKNOWN
}
```

> **⚠️ 注意事项：**
> - `autoResultMap = true` 必须加，否则 JacksonTypeHandler 不生效，JSON 字段反序列化失败。
> - JSON 列对应的 POJO 必须是普通 JavaBean（无参构造 + getter/setter），不需要 `@TableName`。
> - 枚举值存 String 不存 ENUM，方便后续扩展。
> - 布尔字段用 `Integer`（TINYINT 映射），不用 `Boolean`。
> - **高频写入的表（如健康检查历史 `provider_health`）不要继承 BaseEntity** —— 避免逻辑删除和审计字段的写放大。这类表直接 `implements Serializable`，只保留 `id` 和业务字段。

### 1.4 创建 Mapper

**文件位置：** `hify-module-{name}/src/main/java/com/hify/module/{name}/repository/XxxMapper.java`

```java
@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {
    // 简单查询用 MyBatis-Plus LambdaQueryWrapper，不写 SQL
    // 复杂查询（>5 行）才写 @Select 或 XML
}
```

**验证方式：**
```bash
mvn compile -pl hify-module-{name}
```

编译通过即 Entity + Mapper 可用。不需要写测试——MyBatis-Plus BaseMapper 的方法不需要测。

> **⚠️ 注意事项：**
> - MyBatis-Plus 3.5.x 分页插件依赖独立的 `mybatis-plus-jsqlparser` 模块。如果分页报 `PaginationInnerInterceptor` 找不到，检查 `pom.xml` 是否遗漏此依赖。
> - `hify-common` 改动后必须 `mvn install -DskipTests`，再启动 `hify-app`。`mvn compile` 不会更新本地仓库的 jar，导致运行的是旧代码。

---

## Phase 2: DTO 层

### 2.1 创建 DTO

**文件位置：** `hify-module-{name}/src/main/java/com/hify/module/{name}/controller/dto/`

**产出物清单（以 Provider 为例）：**

```
controller/dto/
├── ProviderCreateRequest.java    // 创建入参
├── ProviderUpdateRequest.java    // 更新入参
├── ProviderQueryRequest.java     // 分页查询入参（page, pageSize, 筛选字段）
├── ProviderResponse.java         // 列表/详情出参
├── ProviderHealthResponse.java   // 健康状态出参
├── ConnectionTestResult.java     // 连通性测试结果（既是出参也是业务对象）
├── ProviderModelCreateRequest.java
├── ProviderModelUpdateRequest.java
└── ProviderModelResponse.java
```

**关键规范：**

```java
// Request：用 @NotBlank / @NotNull 做基础校验
@Data
public class ProviderCreateRequest {
    @NotBlank(message = "提供商名称不能为空")
    private String name;

    @NotBlank(message = "提供商编码不能为空")
    private String providerCode;

    private String baseUrl;
    private AuthConfig authConfig;   // 直接复用 entity 下的 POJO
}

// QueryRequest：分页参数 + 筛选条件
@Data
public class ProviderQueryRequest {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String providerCode;     // 筛选字段
    private String status;
}

// Response：不包含敏感字段（如 apiKey 明文），提供 from() 静态工厂
@Data
public class ProviderResponse {
    private Long id;
    private String name;
    private String providerCode;
    private String healthStatus;
    private Integer modelCount;      // 非数据库字段，Service 层填充
    private Long lastHealthResponseTimeMs;  // 同上
    // 注意：不返回 authConfig.apiKey
    // 敏感字段改用布尔标记：private boolean authConfigured;

    /** 从 Entity 构造 Response，额外字段由 Service 补充 */
    public static ProviderResponse from(Provider entity) {
        ProviderResponse resp = new ProviderResponse();
        BeanUtils.copyProperties(entity, resp);
        // apiKey 不拷贝
        return resp;
    }
}
```

> **⚠️ 注意事项：**
> - Request 和 Response 是独立的类，不要复用。创建和更新的 Request 也是独立的。
> - 列表查询建议封装 `XxxQueryRequest`，不要裸传 page/pageSize/筛选字段。
> - Response 中提供 `from(entity)` 静态工厂方法，统一 Entity→Response 转换。
> - Response 中可以包含非数据库字段（如 `modelCount`），Service 层负责填充。
> - API Key 等敏感信息不要包含在 Response 中，用 `authConfigured: boolean` 标记"是否已配置"。
> - DTO 文件数量可能很多（Provider 有 8 个），这是正常的——一实体一 Request/Response 对。
>
> **🔴 PageResult 结构警告：**
> - `PageResult` 必须是一个独立的 POJO，包含 `list`、`total`、`page`、`pageSize` 四个字段。
> - **绝对不要让 `PageResult` 继承 `Result`**。如果继承，序列化后 `data` 字段直接是数组，`total` 在外部被前端拦截器丢弃，导致分页信息丢失。
> - 正确结构：`{ "code": 0, "data": { "list": [...], "total": N, "page": 1, "pageSize": 20 } }`

### 2.2 验证

```bash
mvn compile -pl hify-module-{name}
```

---

## Phase 3: Service 层

### 3.1 创建 Service 接口

**文件位置：** `hify-module-{name}/src/main/java/com/hify/module/{name}/service/XxxService.java`

```java
public interface ProviderService {
    IPage<ProviderResponse> list(int page, int pageSize, String providerCode, String status);
    ProviderResponse getById(Long id);
    ProviderResponse create(ProviderCreateRequest request);
    ProviderResponse update(Long id, ProviderUpdateRequest request);
    void delete(Long id);
}
```

### 3.2 创建 Service 实现

**文件位置：** `hify-module-{name}/src/main/java/com/hify/module/{name}/service/impl/XxxServiceImpl.java`

**每个方法的结构模板：**

```java
@Override
@Transactional(rollbackFor = Exception.class)
public ProviderResponse create(ProviderCreateRequest request) {
    // 1. 业务校验（如名称唯一性）
    if (providerMapper.selectCount(
            new LambdaQueryWrapper<Provider>()
                    .eq(Provider::getName, request.getName())) > 0) {
        throw new BizException(ErrorCode.PARAM_INVALID, "提供商名称已存在");
    }

    // 2. Request → Entity
    Provider entity = new Provider();
    BeanUtils.copyProperties(request, entity);
    entity.setHealthStatus("UNKNOWN");  // 新建默认 UNKNOWN
    entity.setFailCount(0);

    // 3. 持久化
    providerMapper.insert(entity);

    // 4. Entity → Response
    return toResponse(entity);
}
```

**关键规范：**

| 规则 | 说明 |
|------|------|
| `@Transactional(rollbackFor = Exception.class)` | 只标在写方法上（create/update/delete），读方法不加 |
| 事务不包裹外部 IO | 连通性测试、HTTP 调用绝对不能放在事务方法里 |
| 返回值是 DTO | 永远不返回 Entity |
| 异常用 `BizException(ErrorCode)` | 禁止 `throw new RuntimeException("xxx")` |
| 写操作后清缓存 | `@CacheEvict(value = CacheNames.PROVIDER, allEntries = true)` |
| 复杂查询用 LambdaQueryWrapper | 不拼字符串 SQL |
| 跨模块调用走 Service 接口 | 不直接引用其他模块的 Mapper 或 Entity |
| 线程池用 `@Qualifier` 注入 | `@Qualifier("llmExecutor")` 注入 `ThreadPoolTaskExecutor`，禁止 `new Thread()` 或 `Executors` |

### 3.3 模块特有 Service

Provider 模块除了基础 CRUD 还有：

```
service/
├── ProviderService.java               // CRUD
├── ProviderConnectivityService.java   // 连通性测试接口
├── ProviderModelService.java          // 模型管理接口
└── impl/
    ├── ProviderServiceImpl.java
    ├── ProviderConnectivityServiceImpl.java
    └── ProviderModelServiceImpl.java
```

**连通性测试 Service 的特殊注意事项：**
- 纯 IO 操作，不标注 `@Transactional`
- 根据 `providerCode` 分发到不同厂商 API（用 `switch` 表达式）
- 超时时间设为 10s（比业务 LLM 调用的 60s 短很多，因为这只是连通性探测）
- 所有异常统一转为 `ConnectionTestResult`（success=false + errorMessage），不向上抛
- 错误信息用中文，前端可直接展示

**健康检查定时任务的特殊注意事项：**
- 使用 `@ConditionalOnProperty(name = "hify.health-check.enabled", havingValue = "true", matchIfMissing = true)`，mock profile 中设为 `false` 避免无实际 API 时的无意义检查
- 调度方法只做查询 + `asyncExecutor.submit()`，毫秒级返回，不阻塞 `@Scheduled` 单线程池
- 健康状态转换：连接成功 → HEALTHY，连续失败 3 次 → UNHEALTHY，每次检查写入历史表

### 3.4 验证

```bash
mvn compile -pl hify-module-{name}
```

**此时可用 mock profile 快速验证（无需 MySQL/PostgreSQL/Redis）：**
```bash
# 确保 application-mock.yml 中关闭了 Redis 和健康检查
# hify.health-check.enabled=false
# spring.autoconfigure.exclude 排除 RedisAutoConfiguration
mvn spring-boot:run -pl hify-app -Dspring-boot.run.profiles=mock
# 用 @PostConstruct 或临时 main 方法验证 Service 逻辑（验证完删掉）
```

> **⚠️ 注意事项：**
> - mock profile 必须排除 `RedisAutoConfiguration`，否则即使 Redis 没启动也会尝试连接报错。在 `application-mock.yml` 中配置：`spring.autoconfigure.exclude: org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration`
> - 不改 mock profile 的话，`RedisConfig` 加 `@Profile("!mock")` 也可避免 Bean 冲突。
> - **改 `hify-common` 后必须 `mvn install -DskipTests`**，再启动 `hify-app`。`mvn compile` 不会更新本地仓库 jar。

---

## Phase 4: Controller 层

### 4.1 创建 Controller

**文件位置：** `hify-module-{name}/src/main/java/com/hify/module/{name}/controller/XxxController.java`

```java
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;
    private final ProviderConnectivityService connectivityService;

    @GetMapping
    public PageResult<ProviderResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String status) {
        IPage<ProviderResponse> result = providerService.list(
                page != null ? page : 1,
                pageSize != null ? pageSize : 20,
                providerCode, status);
        return PageHelper.toPageResult(result);
    }

    @PostMapping
    public Result<ProviderResponse> create(@Valid @RequestBody ProviderCreateRequest request) {
        return Result.ok(providerService.create(request));
    }

    @PostMapping("/{id}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        return Result.ok(connectivityService.testConnection(id));
    }
}
```

**关键规范：**

| 规则 | 说明 |
|------|------|
| 方法体 ≤15 行 | 只做三件事：接收参数 → 调 Service → 返回 Result |
| 分页返回 `PageResult<T>` | code=0；非分页返回 `Result<T>`，code=200 |
| `@Valid` 校验入参 | 不手动 if-null 判断 |
| Controller 不 try-catch | 全交给 GlobalExceptionHandler |
| `@PathVariable Long id` | ID 类型统一用 Long |

> **⚠️ 注意事项：**
> - **Spring Boot 3.2 必须在 `pom.xml` 的 `maven-compiler-plugin` 中加 `<parameters>true</parameters>`**，否则 `@PathVariable("id")` 的参数名在运行时无法识别 → 400 错误。如果习惯写 `@PathVariable Long id`（靠变量名匹配），这个配置是必须的。
> - 建议显式写 `@PathVariable("id")` 而不是依赖变量名，增强可读性。

### 4.2 验证

```bash
# 编译
mvn compile -pl hify-app

# 启动应用
mvn spring-boot:run -pl hify-app

# curl 逐接口验证（另一终端）
# 1. 登录获取 token
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'

# 2. 分页列表
curl -s http://localhost:8080/api/v1/providers?page=1\&pageSize=20 \
  -H 'Authorization: Bearer <token>'

# 3. 创建
curl -s -X POST http://localhost:8080/api/v1/providers \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d @/tmp/create-provider.json

# 4. 连通性测试
curl -s -X POST http://localhost:8080/api/v1/providers/1/test-connection \
  -H 'Authorization: Bearer <token>'
```

> **⚠️ 注意事项（Windows + bash 环境）：**
> - curl 在 bash（Git Bash）下 JSON 字符串转义容易出错，复杂 JSON 写入临时文件 `-d @/tmp/file.json` 更可靠。
> - 如果端口 8080 被占用，用 `taskkill //F //PID <pid>` 杀旧进程。PowerShell 和 Git Bash 的进程管理命令不同。
> - `//F` 是 Windows 下 taskkill 的写法，不是 `-F`。

---

## Phase 5: 前端对接

### 5.1 创建 API 文件

**文件位置：** `hify-web/src/api/{module}.ts`

**产出物：** 一个文件包含该模块所有 API 函数和 TypeScript 类型定义。

```typescript
// api/provider.ts
import { get, post, put, del } from '@/utils/request'

// ---- 类型 ----
export interface ProviderResponse {
  id: number
  name: string
  providerCode: string
  baseUrl: string
  healthStatus: string
  status: string
  modelCount: number
  lastHealthResponseTimeMs: number | null
  createdAt: string
  updatedAt: string
  // authConfig 不包含 apiKey 明文
}

export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount: number
  errorMessage: string | null
}

export interface ProviderCreateData {
  name: string
  providerCode: string
  baseUrl?: string
  description?: string
  authConfig?: Record<string, any>
}

// ---- API 函数 ----
export function getProviderList(params: Record<string, any>) {
  return get<PageResult<ProviderResponse>>('/v1/providers', { params })
}

export function createProvider(data: ProviderCreateData) {
  return post<ProviderResponse>('/v1/providers', data)
}

export function testConnection(providerId: number) {
  return post<ConnectionTestResult>(`/v1/providers/${providerId}/test-connection`)
}
```

> **⚠️ 注意事项：**
> - `request.ts` 的响应拦截器已经处理了 code 判断：code=200 → 自动解包 data，code=0（PageResult）→ 返回完整对象。所以 API 函数返回的 Promise 类型是 `T` 而不是 `Result<T>`。
> - 列表接口返回类型写 `PageResult<T>`（包含 list/total/page/pageSize），对应后端解包后的 `data` 字段。
> - 后端 Java 的 `Long` 类型到前端是 `number`，但 JS number 只有 53 位精度。ID 不会超过这个范围，所以可以安全使用 `number`。
> - 前端下拉框的 `value` 必须与后端 switch case 的值完全一致，区分大小写。踩坑：前端 `value="claude"` 而后端 `case "anthropic"` → 连通性测试返回"不支持的提供商类型"。
> - **Vite 代理：** `/api` → `http://localhost:8080`。前端 baseURL 写 `/api`，后端路径 `/api/v1/xxx` 完整保留。不需要在 `vite.config.ts` 额外配置跨域。

### 5.2 创建页面组件

**文件位置：** `hify-web/src/views/{module}/XxxList.vue`

**Provider 模块使用的前端模式（可直接复用）：**

| 组件 | 用途 |
|------|------|
| `HifyTable` | 通用表格（分页、展开行、列配置） |
| `HifyFormDialog` | 通用表单弹窗（新增/编辑共用） |
| `PageHeader` | 页面标题栏 |

**页面结构模板：**

```vue
<script setup lang="ts">
// 1. 数据状态：list, loading, pagination
// 2. 表单弹窗：dialogVisible, dialogTitle, editingId
// 3. CRUD 方法：fetchList → loadModels 批量填充 → 渲染
// 4. handleSubmit：create / update → fetchList
// 5. handleDelete：confirm → delete → fetchList
// 6. handleTestConnection：调用 API → ElMessage 展示结果
</script>

<template>
  <PageHeader title="模型提供商" description="管理 LLM 提供商的连接配置和 API Key" />
  <HifyTable :columns="columns" :data="list" :total="total" @page-change="fetchList" />
  <HifyFormDialog :visible="dialogVisible" :title="dialogTitle" :form-data="formData"
      :fields="formFields" :rules="formRules" @submit="handleSubmit" @close="dialogVisible = false" />
</template>
```

> **⚠️ 注意事项：**
> - `HifyFormDialog` 的 `handleConfirm` emit 不等待异步 handler，所以 loading 状态在快速操作时可能不可靠。这是已知的 UI 体验问题，不是功能 bug。
> - 表格的 `modelCount` 列做成可展开的——点击后调 `getProviderModels(providerId)` 加载模型列表。
> - 健康状态用 `el-tag` 展示：HEALTHY=绿色、UNHEALTHY=红色、DEGRADED=黄色、UNKNOWN=灰色。
> - SSE 流式渲染（conversation 模块）需要独立封装 `useSSE` composable，不能直接用 fetch。
> - **不要在前端 `env.d.ts` 中写 `declare module '*.vue' { ... }`**——这会覆盖 Volar 的真实类型推断，导致组件 ref 的 expose 方法找不到。Vue 3 + Volar 已经内置了 `.vue` 模块的类型声明。

### 5.3 验证

```bash
# TypeScript 类型检查
npx vue-tsc --noEmit

# 前端 dev server
npm run dev
```

在浏览器 DevTools → Network 确认：
- 请求打到后端（状态码 200，非 `ERR_CONNECTION_REFUSED`）
- 响应 `data.list` 是数组，`data.total` 是数字
- 表格有数据渲染（或显示"暂无数据"而非一直转圈）

> **⚠️ 如果页面一直转圈：**
> 1. 先看 Network 标签确认请求状态码——是 pending 还是 500？
> 2. `ERR_CONNECTION_REFUSED` → 后端未启动或端口不对
> 3. 状态码 200 但一直转圈 → 看响应体 `data` 结构是否正确（最常见是 `total` 丢失导致分页组件异常）
> 4. 状态码 401 → token 过期，重新登录

浏览器打开 `http://localhost:5173`，手动操作验证：
- 列表加载正常
- 新增/编辑弹窗字段正确
- 连通性测试结果展示正确
- 分页切换正常

---

## Phase 6: 完整验收

### 6.1 后端验收清单

```bash
# 1. 编译通过
mvn compile

# 2. 启动成功（两种方式都要验证）
# 方式 A：真实数据库
mvn spring-boot:run -pl hify-app
# 方式 B：mock profile（无 MySQL/Redis 依赖，适合快速验证和 CI）
mvn spring-boot:run -pl hify-app -Dspring-boot.run.profiles=mock

# 检查日志：无 Bean 注入失败、无端口冲突、无配置缺失

# 3. 逐接口 curl 验证
#   - GET  /api/v1/{resources}          分页列表
#   - GET  /api/v1/{resources}/{id}     详情
#   - POST /api/v1/{resources}          创建
#   - PUT  /api/v1/{resources}/{id}     更新
#   - DELETE /api/v1/{resources}/{id}   删除
#   - POST /api/v1/{resources}/{id}/... 模块特有操作（如连通性测试）

# 4. 异常场景验证
#   - 不传 token → 401
#   - 传无效 ID → 业务异常 + 中文提示
#   - 重复名称创建 → 业务异常
#   - 缺少必填字段 → 400 + 参数校验失败提示

# 5. 定时任务验证（如有）
#   - 观察日志中等 60s 后调度触发
#   - 检查数据库历史表有记录写入
```

### 6.2 前端验收清单

```
□ TypeScript 编译零错误（vue-tsc --noEmit）
□ 页面正常渲染（无白屏/控制台报错）
□ 列表分页正确
□ 新增弹窗：必填校验生效、提交后列表刷新
□ 编辑弹窗：回填数据正确、提交后列表刷新
□ 删除：确认弹窗 → 删除成功 → 列表刷新
□ 连通性测试：点击按钮 → 展示结果（成功/失败+延迟+模型数）
□ 健康状态：UNKNOWN(灰) → 定时任务检查后变为 HEALTHY(绿) 或 UNHEALTHY(红)
□ 模型数展开：点击可展开 → 展示模型列表
```

### 6.3 数据一致性验证

```sql
-- 检查 provider 表
SELECT id, name, provider_code, health_status, fail_count, last_health_check_at
FROM hify_provider WHERE deleted = 0;

-- 检查健康检查历史
SELECT provider_id, health_status, response_time_ms, fail_reason, checked_at
FROM hify_provider_health ORDER BY checked_at DESC LIMIT 10;

-- 确认创建时间与更新时间一致（新建时）
-- 确认定时任务每 60s 写入一条健康记录
```

---

## 踩坑记录（来自 Provider 模块实战）

### 1. providerCode 前后端不一致

**现象：** 前端下拉框 `value="claude"`，后端 switch case 是 `"anthropic"`，连通性测试返回"不支持的提供商类型"。

**根因：** 数据库 schema.sql 里 `provider_code` 设计用 `claude`，后端 Switch 用了历史代码的 `case "anthropic"`。

**教训：** 前后端对接时，枚举值以**数据库 schema.sql 为准**。建一个新文件 `{模块}Constants.java` 集中定义所有字符串常量，前后端各引用一份，避免散落各处的魔术字符串。

### 2. Redis 未启动但应用正常运行

**现象：** Redis 没启动，应用启动成功，缓存功能静默不可用。

**根因：** `RedisConfig` 和 `CacheConfig` 都加了 `@ConditionalOnBean(RedisConnectionFactory.class)`，Redis 不可用时自动降级。这是设计行为不是 bug。

**教训：** 开发调试时记得检查 Redis 状态。验收报告中明确标注"Redis 不可用，缓存功能降级"。

### 3. Windows Git Bash 下 curl JSON 转义

**现象：** `curl -d '{"key":"value"}'` 在 Git Bash 里 JSON 被错误转义，后端报"请求体格式错误"。

**解决：** 将 JSON 写入临时文件 `echo '{"key":"value"}' > /tmp/body.json`，然后 `curl -d @/tmp/body.json`。避免在 Windows 终端做 JSON 内联转义。

### 4. 端口冲突

**现象：** 启动报 `Port 8080 already in use`，上次启动的 Java 进程还在。

**解决：**
```bash
# Git Bash 下
netstat -ano | grep 8080          # 找到 PID
taskkill //F //PID <pid>          # Windows 下用 //F 不是 -F
```

### 5. PageResult code=0 vs Result code=200

**现象：** 前端 `request.ts` 拦截器判断 `code === 200` 才解包 data，但分页接口返回 `code=0`。

**根因：** `PageResult.ok()` 设置 `code=0` 以区分普通 `Result.ok()` 的 `code=200`。

**解决：** `request.ts` 拦截器需要同时处理 `code === 200`（解包 data）和 `code === 0`（返回完整 PageResult）。

### 6. JacksonTypeHandler 不生效

**现象：** JSON 列（如 `auth_config`）读出来是 null 或空对象。

**根因：** `@TableName` 没加 `autoResultMap = true`。

**解决：** `@TableName(value = "hify_provider", autoResultMap = true)` 必须开启，MyBatis-Plus 才会使用 `@TableField(typeHandler = JacksonTypeHandler.class)`。

### 7. @Scheduled 定时任务阻塞

**现象：** 定时任务不触发或间隔不均匀。

**根因：** 默认 `@Scheduled` 用单线程执行，如果上一个任务没完成，下一个要排队。

**解决：** 调度方法体只做查询+提交，实际 IO 用 `asyncExecutor.submit()` 异步执行。调度线程几十毫秒内返回，不会被阻塞。

### 8. 连通性测试不能放事务里

**现象：** 如果在 `@Transactional` 方法里调 LLM API，数据库连接被持有 10-60 秒。

**教训：** 连通性测试 Service 不标注 `@Transactional`，它是纯 IO 操作。记住 CLAUDE.md 铁律："事务绝不包裹 LLM 调用或任何外部 IO"。

### 9. PageResult 继承 Result 导致分页数据丢失

**现象：** 前端表格能拿到数据，但 total 始终为 0，分页组件不显示。

**根因：** `PageResult` 继承了 `Result`，序列化后 `data` 字段直接是数组 `[...]`，`total` 被序列化到外层。前端 `request.ts` 解包 `response.data.data` 时只拿到数组，丢失了分页元数据。

**解决：** `PageResult` 必须是独立 POJO，包含 `list`、`total`、`page`、`pageSize` 四个字段。正确 JSON 结构：`{ "code": 0, "message": "success", "data": { "list": [...], "total": N, "page": 1, "pageSize": 20 } }`。

### 10. @PathVariable 参数名丢失 → 400 错误

**现象：** `@PathVariable Long id` 报 400，但 `@PathVariable("id") Long id` 正常。

**根因：** Spring Boot 3.2 默认不在 class 文件中保留方法参数名，运行时反射拿不到 `id` 这个名称。

**解决：** `pom.xml` 的 `maven-compiler-plugin` 添加 `<parameters>true</parameters>`。或者统一显式写 `@PathVariable("id")`（推荐后者，代码更清晰）。

### 11. 改 hify-common 后运行还是旧代码

**现象：** 修改了 `hify-common` 中的工具类，跑 `mvn spring-boot:run` 后发现行为没变。

**根因：** `mvn compile` 只编译当前模块，不会把 jar 安装到本地仓库。`hify-app` 依赖的是本地仓库中的 `hify-common-x.x.x.jar`，还是旧版本。

**解决：** 修改 `hify-common` 后，先 `mvn install -DskipTests -pl hify-common`，再启动 `hify-app`。

### 12. MyBatis-Plus 分页插件 ClassNotFoundException

**现象：** 分页查询报 `PaginationInnerInterceptor` 找不到。

**根因：** MyBatis-Plus 3.5.x 把 JSqlParser 拆分成了独立模块 `mybatis-plus-jsqlparser`，不引入则无法使用分页。

**解决：** 在用到分页的模块 `pom.xml` 中显式添加 `mybatis-plus-jsqlparser` 依赖。

### 13. env.d.ts 覆盖 Volar 类型推断

**现象：** 在父组件中通过 ref 调用子组件 expose 的方法，TypeScript 报"类型上不存在属性 xxx"。

**根因：** 手写了 `declare module '*.vue'` 把所有 `.vue` 文件类型覆盖为 `any`。

**解决：** 删除手写的 `declare module '*.vue'`。Vue 3 + Volar 已内置正确的 `.vue` 类型声明。

---

## 踩坑速查表

| 现象 | 原因 | 修复 |
|------|------|------|
| 页面一直转圈 | 后端未启动 / 请求 pending | 先看 Network 状态码 |
| 列表有数据但 total=0 不显示分页 | PageResult 继承 Result 导致 data 是数组 | PageResult 改为普通 POJO，data 包含 {list,total} |
| `@PathVariable` 400 错误 | 缺少 `-parameters` 编译参数 | pom.xml compiler plugin 加 `<parameters>true</parameters>` |
| JSON 字段反序列化 null | 缺少 autoResultMap=true 或 JacksonTypeHandler | Entity 加 `@TableName(autoResultMap = true)` |
| mock profile 启动失败 Bean 冲突 | RedisConfig 未排除 | 加 `@Profile("!mock")` 或 exclude auto-config |
| H2 启动报 SQL 错误 | schema.sql 用了 MySQL 专属语法 | 维护独立 schema-h2.sql，JSON→CLOB |
| 改 hify-common 后运行旧代码 | spring-boot:run 用了旧 jar | 先 `mvn install -DskipTests -pl hify-common` |
| 分页报 PaginationInnerInterceptor 找不到 | 缺少 jsqlparser 依赖 | 加 `mybatis-plus-jsqlparser` 依赖 |
| 前端 ref 调子组件方法 TS 报错 | env.d.ts 手写 `declare module '*.vue'` | 删除手写声明 |
| 连通性测试返回"不支持的提供商类型" | providerCode 前后端不一致 | 以 schema.sql 为准，建常量类集中管理 |
| `@Scheduled` 不触发 | 任务阻塞了调度线程 | 调度方法只提交异步任务，不执行 IO |
| curl JSON 转义错误 | Windows Git Bash 内联 JSON | `-d @/tmp/file.json`

以下文件/模式在 Provider 模块中已验证，其他模块可以直接复用：

| 复用什么 | 来源 | 说明 |
|----------|------|------|
| `BaseEntity.java` | `hify-common` | id/createdAt/updatedAt/deleted，所有 Entity 继承 |
| `Result<T>` / `PageResult<T>` | `hify-common` | 统一响应体，Controller 返回值 |
| `BizException` / `ErrorCode` | `hify-common` | 业务异常 + 错误码枚举 |
| `GlobalExceptionHandler` | `hify-common` | 全局异常转译，Controller 不用 try-catch |
| `LlmHttpClient` | `hify-common` | GET/POST/SSE 带超时和错误分类 |
| `PageHelper` | `hify-common` | MyBatis-Plus IPage → PageResult 转换 |
| `JwtInterceptor` | `hify-app` | 从 Authorization header 解析 JWT |
| `HifyTable.vue` | `hify-web` | 通用表格（分页+展开行+列配置） |
| `HifyFormDialog.vue` | `hify-web` | 通用表单弹窗（新增/编辑复用） |
| `request.ts` | `hify-web` | axios 实例（JWT 注入+响应解包+错误处理） |
| Provider 模块完整代码 | `hify-module-provider` | MVC 三层样板，新模块直接模仿文件结构 |

---

## 快速检查清单

每个 Phase 完成前自查：

**Phase 1 (Schema + Entity + Mapper):**
- [ ] schema.sql 和 schema-h2.sql 都已更新
- [ ] Entity 继承 BaseEntity（高频写入表例外，直接实现 Serializable）
- [ ] `@TableName(autoResultMap = true)`
- [ ] JSON 列标注 `@TableField(typeHandler = JacksonTypeHandler.class)`
- [ ] Mapper 继承 `BaseMapper<XxxEntity>` + `@Mapper`
- [ ] 枚举值用 String 不用 ENUM
- [ ] `mybatis-plus-jsqlparser` 依赖已添加（如需分页）

**Phase 2 (DTO):**
- [ ] 有 XxxQueryRequest（分页+筛选）、XxxCreateRequest、XxxUpdateRequest、XxxResponse
- [ ] Request 有 `@NotBlank`/`@NotNull` 校验注解
- [ ] Response 含 `from(entity)` 静态工厂方法
- [ ] Response 不含敏感字段（apiKey 等），用 `authConfigured: boolean` 代替
- [ ] PageResult 是独立 POJO，不继承 Result

**Phase 3 (Service):**
- [ ] 写方法有 `@Transactional(rollbackFor = Exception.class)`
- [ ] 异常用 `BizException(ErrorCode)`
- [ ] 返回值是 DTO，不返回 Entity
- [ ] 不拼接 SQL 字符串
- [ ] 跨模块调用走 Service 接口
- [ ] 线程池用 `@Qualifier` 注入
- [ ] 外部 IO（连通性测试等）不在事务方法内
- [ ] 定时任务有 `@ConditionalOnProperty`，mock profile 可关闭

**Phase 4 (Controller):**
- [ ] 方法体 ≤15 行
- [ ] 入参有 `@Valid`
- [ ] 返回 `Result<T>` 或 `PageResult<T>`
- [ ] `@PathVariable` 显式写参数名 `@PathVariable("id")`
- [ ] 不 try-catch
- [ ] `maven-compiler-plugin` 有 `<parameters>true</parameters>`

**Phase 5 (前端):**
- [ ] TypeScript 类型与后端 DTO 字段对应
- [ ] 下拉框 value 与后端枚举值一致
- [ ] `env.d.ts` 没有手写 `declare module '*.vue'`
- [ ] `vue-tsc --noEmit` 零错误

**Phase 6 (验收):**
- [ ] 全部 CRUD curl 通过（真实 DB + mock profile 两种方式）
- [ ] 异常场景返回正确错误码+中文提示
- [ ] 浏览器全流程走通
- [ ] Network 面板确认响应结构正确（data.list 是数组，data.total 是数字）
- [ ] 定时任务（如有）写入历史记录
