# Hify 代码组织规范 v1.0

> **适用场景：** Spring Boot 单体应用 + MyBatis-Plus，1 人开发，模块化单体架构。
> **核心原则：** 模块高内聚、跨模块走 shared 契约、每一层只做自己该做的事。

---

## 一、顶层目录结构

```
com.hify
├── common                              // 全局基础设施（不放业务逻辑）
│   ├── config                          // Spring 配置类
│   ├── exception                       // 全局异常定义 + 全局异常处理器
│   ├── web                             // 全局 Web 层增强（统一响应体、分页封装）
│   └── util                            // 纯工具类（字符串、日期、加解密）
│
├── module                              // 业务模块
│   ├── provider                        // 模型提供商管理
│   ├── agent                           // Agent 配置
│   ├── conversation                    // 对话引擎
│   ├── knowledge                       // 知识库 RAG
│   ├── workflow                        // 简版工作流
│   └── mcp                             // MCP 工具接入
│
└── shared                              // 模块间共享契约（只有接口 + DTO，没有实现）
    ├── llm                             // LLM 统一调用接口
    ├── tool                            // 工具执行接口
    └── rag                             // 检索增强接口
```

### 铁律

| 规则 | 说明 |
|---|---|
| `common` 里不写业务代码 | 如果一段代码用到了业务 Entity/DTO，它不属于 common |
| `shared` 里只有接口和 POJO | 绝不允许出现 `@Service`、`@Component`、实现类 |
| `module` 之间不互相 import | `module/a` 绝对不能 `import module.b.XxxService` |
| 模块间通信只通过 `shared` 接口 | 用 Spring 的依赖注入：模块提供实现，调用方注入接口 |

---

## 二、单个模块内部的目录结构

每个 `module/<name>` 严格按以下三层组织：

```
module/<name>/
├── controller          // 接入层：接收 HTTP 请求，参数校验，调用 Service，返回响应
│   ├── XxxController.java
│   └── dto
│       ├── XxxCreateRequest.java      // 入参：创建
│       ├── XxxUpdateRequest.java      // 入参：更新
│       ├── XxxQueryRequest.java       // 入参：查询
│       └── XxxResponse.java           // 出参：响应（也可能是 VO）
│
├── service             // 业务层：编排业务逻辑，管理事务边界
│   ├── XxxService.java                // 业务服务接口
│   └── impl
│       └── XxxServiceImpl.java        // 业务服务实现
│
└── repository          // 数据层：只和数据库打交道，不包含业务逻辑
    ├── XxxMapper.java                 // MyBatis-Plus Mapper 接口
    └── entity
        └── XxxEntity.java             // 数据库实体
```

### 2.1 Controller 层——铁律

```java
// ✅ 正确示范
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @PostMapping
    public R<ProviderResponse> create(@Valid @RequestBody ProviderCreateRequest request) {
        return R.ok(providerService.create(request));
    }

    @GetMapping("/{id}")
    public R<ProviderResponse> getById(@PathVariable Long id) {
        return R.ok(providerService.getById(id));
    }
}
```

| 规则 | 说明 |
|---|---|
| Controller 只做三件事 | ① 参数校验（`@Valid`）② 调 Service ③ 返回统一响应 `R<T>` |
| Controller 不写业务逻辑 | 判断、计算、循环、调用第三方 API 统统不准出现在 Controller |
| 入参用独立 DTO 类 | 不允许直接用 Entity 接收请求参数 |
| 一个 Controller 不超过 8 个 public 方法 | 超过了说明该拆成两个 Controller |

### 2.2 Service 层——铁律

```java
// ✅ 正确示范
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private final ProviderMapper providerMapper;
    // 跨模块调用只注入 shared 接口，不注入其他 module 的 Service
    private final LlmTestService llmTestService; // 这是 shared 里的接口

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProviderResponse create(ProviderCreateRequest request) {
        // 1. 业务校验
        validateNotDuplicate(request.getName());

        // 2. 构建实体
        ProviderEntity entity = buildEntity(request);

        // 3. 持久化
        providerMapper.insert(entity);

        // 4. 返回结果
        return toResponse(entity);
    }
}
```

| 规则 | 说明 |
|---|---|
| Service 是事务边界 | `@Transactional` 只标在 Service 方法上，不标在 Controller 或 Mapper |
| Service 方法粒度 | 一个 public 方法对应一个完整的业务动作（创建提供商、查询提供商列表……） |
| 禁止 `Entity` 穿透到 Controller | Service 返回的是 DTO/Response，不是 Entity |
| 禁止在 Service 中操作 HttpServletRequest/Response | 那是 Controller 的事 |
| 跨模块调用只依赖 `shared` 包下的接口 | `@Autowired private shared.llm.LlmProviderApi xxx;` ✅ `/ @Autowired private module.provider.service.XxxService xxx;` ❌ |

### 2.3 Repository 层——铁律

```java
// ✅ 正确示范
@Mapper
public interface ProviderMapper extends BaseMapper<ProviderEntity> {
    // MyBatis-Plus BaseMapper 已提供 CRUD
    // 复杂查询在这里定义，用 @Select 或 XML
    @Select("SELECT * FROM provider WHERE name = #{name} AND deleted = 0")
    ProviderEntity selectByName(@Param("name") String name);

    IPage<ProviderEntity> selectPage(Page<ProviderEntity> page, @Param("query") ProviderQueryRequest query);
}

// Entity 示例
@Data
@TableName("provider")
public class ProviderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String providerType;   // OPENAI, ANTHROPIC, OLLAMA
    private String apiKey;         // 加密存储
    private String baseUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;       // MyBatis-Plus 逻辑删除
}
```

| 规则 | 说明 |
|---|---|
| Mapper 只做数据访问 | 不含业务判断，不含字符串拼接业务逻辑 |
| Entity 是表的 1:1 映射 | 一个 Entity 对应一张数据库表，字段名用下划线转驼峰 |
| 禁止 Entity 出现在 Controller 入参和出参 | Entity 的生命周期止于 Service 层 |
| 禁止在 Entity 中写业务方法 | Entity 只有字段 + getter/setter，不要加 `validate()` `activate()` 等方法 |
| 逻辑删除用 `@TableLogic` | 不要自己在 SQL 里拼 `deleted = 0` |

---

## 三、DTO 规范

DTO 命名严格按用途分类，放在对应层的 `dto` 子包：

| 后缀 | 用途 | 存放位置 | 示例 |
|---|---|---|---|
| `*Request` | HTTP 请求入参 | `controller/dto/` | `ProviderCreateRequest` |
| `*Response` | HTTP 响应出参 / 给前端的数据 | `controller/dto/` | `ProviderResponse` |
| `*DTO` | 跨模块/共享层传递的数据对象 | `shared/xxx/` | `LlmRequestDTO`、`LlmResponseDTO` |

### 入参校验规范

```java
@Data
public class ProviderCreateRequest {
    @NotBlank(message = "提供商名称不能为空")
    @Size(max = 50, message = "名称最长 50 字符")
    private String name;

    @NotBlank(message = "提供商类型不能为空")
    @EnumValue(enumClass = ProviderType.class, message = "无效的提供商类型")
    private String providerType;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    private String baseUrl;
}
```

| 规则 | 说明 |
|---|---|
| 简单校验用 JSR-303 注解 | `@NotBlank` `@NotNull` `@Size` `@Email`，自定义用 `@EnumValue` |
| 涉及数据库的校验放 Service | 比如名称唯一性校验，不走注解，在 Service 里查库判断 |
| 必须加 `message` | 每条校验注解都要指定中文错误提示 |

---

## 四、统一响应体

```java
// common/web/R.java
@Data
public class R<T> {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
```

| 规则 | 说明 |
|---|---|
| 所有 Controller 返回值必须是 `R<T>` | 统一前端/API 消费体验 |
| `code=0` 表示成功 | 非 0 都是错误，错误码在 `common/exception` 统一定义 |
| 不在 `R` 上加 `@JsonInclude` | 字段即使为 null 也返回，方便前端处理 |

---

## 五、异常处理

```java
// common/exception/BusinessException.java
public class BusinessException extends RuntimeException {
    private final int code;
}

// common/exception/ErrorCode.java
public enum ErrorCode {
    PROVIDER_NOT_FOUND(10001, "模型提供商不存在"),
    PROVIDER_NAME_DUPLICATE(10002, "提供商名称已存在"),
    AGENT_NOT_FOUND(20001, "Agent 不存在"),
    CONVERSATION_NOT_FOUND(30001, "对话不存在"),
    // ...
}

// common/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(400, msg);
    }
}
```

| 规则 | 说明 |
|---|---|
| 业务异常只抛 `BusinessException` | 禁止直接抛 `RuntimeException("xxx")` |
| 错误码分段管理 | 1xxxx = provider，2xxxx = agent，3xxxx = conversation，4xxxx = knowledge，5xxxx = workflow，6xxxx = mcp |
| Service 抛异常，Controller 不 try-catch | 全交给 `GlobalExceptionHandler` |
| 禁止 `catch (Exception e) {}` 空处理 | 要么处理，要么抛出去，不要吞异常 |

---

## 六、跨模块调用规则

### 6.1 允许的调用路径

```
Controller  ──调用──▶  Service  ──调用──▶  Mapper
                           │
                           ├──▶ 同模块 Service（不推荐，优先复用自己）
                           │
                           └──▶ shared 接口（跨模块唯一通道）
                                      ▲
                                      │
                              某个 module 的 Service
                              实现了这个 shared 接口
```

### 6.2 shared 接口定义

```java
// shared/llm/LlmProviderApi.java（接口 + 纯 POJO）
public interface LlmProviderApi {

    /**
     * 同步调用 LLM，返回完整响应
     */
    LlmResponseDTO chat(LlmRequestDTO request);

    /**
     * 流式调用 LLM，通过 consumer 逐块回调
     */
    void chatStream(LlmRequestDTO request, Consumer<String> onChunk);
}

// shared/llm/LlmRequestDTO.java
@Data
public class LlmRequestDTO {
    private Long providerId;           // 指定模型提供商
    private String model;              // 模型名
    private List<MessageDTO> messages; // 对话历史
    private List<ToolDefDTO> tools;    // 可用工具定义（Agent 场景）
    private Double temperature;
    private Integer maxTokens;
}

// shared/llm/LlmResponseDTO.java
@Data
public class LlmResponseDTO {
    private String content;
    private List<ToolCallDTO> toolCalls; // Agent 工具调用
    private UsageDTO usage;              // Token 用量
}
```

### 6.3 模块提供 shared 接口的实现

```java
// module/provider/service/impl/LlmProviderApiImpl.java
@Service
@RequiredArgsConstructor
public class LlmProviderApiImpl implements LlmProviderApi {

    private final ProviderMapper providerMapper;
    private final OpenAiClient openAiClient;       // infra 层的 HTTP 客户端
    private final AnthropicClient anthropicClient;

    @Override
    public LlmResponseDTO chat(LlmRequestDTO request) {
        ProviderEntity provider = providerMapper.selectById(request.getProviderId());
        if (provider == null) {
            throw new BusinessException(ErrorCode.PROVIDER_NOT_FOUND);
        }
        // 按 providerType 路由到对应客户端...
    }
}
```

### 6.4 调用方使用 shared 接口

```java
// module/conversation/service/impl/ConversationServiceImpl.java
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    // 注入 shared 接口，不是注入 provider 模块的类
    private final LlmProviderApi llmProviderApi;
    private final ToolExecutorApi toolExecutorApi;

    @Override
    public MessageResponse sendMessage(SendMessageRequest request) {
        // 通过 shared 接口调 LLM
        LlmResponseDTO response = llmProviderApi.chat(buildRequest(request));
        // ...
    }
}
```

### 6.5 跨模块调用检查清单

| 场景 | 做法 |
|---|---|
| conversation 模块需要调 LLM | ✅ 注入 `shared.llm.LlmProviderApi`，由 provider 模块提供实现 |
| agent 模块需要执行工具 | ✅ 注入 `shared.tool.ToolExecutorApi`，由 mcp 模块提供实现 |
| conversation 模块需要查 Agent 配置 | ✅ 注入 `shared.agent.AgentConfigApi`（新定义一个），由 agent 模块提供实现 |
| conversation 需要直接调 agent 的 Service | ❌ 不允许。定义 shared 接口，agent 实现 |
| 两个模块需要相同的数据结构 | ✅ 放 `shared` 里作为共享 DTO |
| 某段逻辑 3 个模块都要用 | ✅ 判断是否放在 `shared` 即可，如果 shared 接口不够就新定义一个 |

---

## 七、MyBatis-Plus 约定

| 规则 | 说明 |
|---|---|
| 所有表必须有 `id`、`created_at`、`updated_at` | Entity 可以继承一个 `BaseEntity` |
| 逻辑删除统一用 `@TableLogic` + `deleted` 字段 | tinyint，0=正常，1=删除 |
| 分页统一用 MyBatis-Plus 的 `Page<T>` | Controller 入参接收 `page` `size` 参数 |
| Mapper XML 放 `resources/mapper/` | `resources/mapper/ProviderMapper.xml` |
| 禁止在 Service 层写 SQL | 所有 SQL 语句（包括 `@Select`）只在 Mapper 层出现 |
| 复杂查询优先用 XML 而不是 `@Select` | 超过 5 行的 SQL 用 XML，方便格式化阅读 |

---

## 八、配置与多环境

```
resources/
├── application.yml                    // 公共配置 + 默认值
├── application-dev.yml                // 开发环境
└── application-prod.yml               // 生产环境
```

```yaml
# application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:localhost}:3306/hify
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:}

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

| 规则 | 说明 |
|---|---|
| 敏感信息（密码、密钥）只用环境变量 | 绝不硬编码在 yml 里，`${ENV_VAR:}` 语法 |
| 公共配置放 `application.yml` | 环境差异放 `application-{profile}.yml` |
| 生产配置尽量通过环境变量注入 | 避免在 yml 里写死生产环境的值 |

---

## 九、检查清单（AI 执行用）

当新增一个功能时，按此清单逐项检查工程结构是否合规：

### 新增模块时

1. [ ] 模块目录在 `module/<name>/` 下，含 `controller/` `service/` `repository/` 三个子包
2. [ ] Controller 入参使用 `controller/dto/` 下的独立 Request 类，不直接用 Entity
3. [ ] Controller 方法体在 15 行以内，只做校验+调用+返回
4. [ ] Service 方法标注 `@Transactional(rollbackFor = Exception.class)`
5. [ ] Service 返回值是 DTO/Response，不返回 Entity
6. [ ] Mapper 继承 `BaseMapper<XxxEntity>`
7. [ ] Entity 使用 `@TableName` `@TableId` `@TableLogic` 注解
8. [ ] 错误码在 `common/exception/ErrorCode` 中定义，按模块号段分配
9. [ ] 业务异常只抛 `BusinessException`

### 跨模块调用时

1. [ ] 被调用的功能是否在 `shared/` 下已有接口定义？
2. [ ] 如果没有，先在 `shared/<domain>/` 下定义接口 + DTO，再让提供方实现
3. [ ] 调用方只注入 `shared` 下的接口，不 import `module/xxx/service/XxxService`
4. [ ] shared 接口的参数和返回值全部是 `shared` 包下的 DTO，不引用任何 module 的类
5. [ ] 检查 `module/<name>/` 之间没有任何 `import` 依赖

### 代码 review 自查

1. [ ] Controller 有没有超过 8 个 public 方法？（超过说明要拆）
2. [ ] Service 有没有超过 300 行？（超过说明要把子逻辑提取成私有方法或独立 Service）
3. [ ] 有没有 Entity 泄漏到 Controller 层？
4. [ ] 有没有 `catch (Exception) {}` 空块？
5. [ ] 有没有硬编码的错误消息字符串？（换成 `ErrorCode` 枚举）
6. [ ] 有没有 `System.out.println`？（换成 SLF4J 日志）
7. [ ] application.yml 里有没有硬编码的密码/密钥？

---

## 十、常用代码片段

### 分页查询模板

```java
// Controller
@GetMapping
public R<IPage<ProviderResponse>> list(@Valid ProviderQueryRequest query) {
    return R.ok(providerService.listByPage(query));
}

// Service
public IPage<ProviderResponse> listByPage(ProviderQueryRequest query) {
    Page<ProviderEntity> page = new Page<>(query.getPage(), query.getSize());
    IPage<ProviderEntity> result = providerMapper.selectPage(page, query);
    return result.convert(this::toResponse);
}
```

### 统一异常模板

```java
// 在 Service 中抛异常
if (providerMapper.selectByName(name) != null) {
    throw new BusinessException(ErrorCode.PROVIDER_NAME_DUPLICATE);
}
```

### shared 接口 + 实现模板

```java
// === shared/agent/AgentConfigApi.java ===
public interface AgentConfigApi {
    AgentConfigDTO getById(Long agentId);
    List<AgentConfigDTO> listByIds(List<Long> agentIds);
}

// === module/agent/service/impl/AgentConfigApiImpl.java ===
@Service
@RequiredArgsConstructor
public class AgentConfigApiImpl implements AgentConfigApi {

    private final AgentMapper agentMapper;

    @Override
    public AgentConfigDTO getById(Long agentId) {
        AgentEntity entity = agentMapper.selectById(agentId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        return BeanUtil.copyProperties(entity, AgentConfigDTO.class);
    }
}
```

---

> **最后一条规则：当觉得"这次情况特殊，违规一下问题不大"时——不要违规。规范就是为了防止你三个月后回来看这段代码时骂自己。**
