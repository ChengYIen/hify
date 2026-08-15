---
name: integration-test
description: 为任意 Hify 模块编写 Spring Boot 集成测试的固化流程。当用户输入 /集成测试 后直接跟目标模块或链路（如 /集成测试 agent模块）、或要求"规划集成测试清单/写集成测试/继续P1"时自动启用。流程包括先读配置确认 mock profile、按 P0/P1/P2 输出 IT 清单、按 mock 策略决策表处理外部依赖、从最简单场景递进执行、对已知 bug 先写红测试再修实现。
---

# Hify 集成测试流程：/集成测试

## 触发方式

- 用户输入 `/集成测试 <目标>`，例如 `/集成测试 agent模块`、`/集成测试 对话链路`，中间不加空格。
- 用户说"规划集成测试清单""写 XXX 的集成测试""继续P1""先写场景X"等语义时自动启用。
- 对任意模块都直接按本流程执行，不需要用户重复说明步骤。

## 铁律

1. 先读配置，再出清单，最后写代码；清单要等用户确认。
2. 清单阶段不写测试代码，也不写实现代码。
3. 从最简单、依赖最少的场景开始，跑通一个再写下一个，不批量生成。
4. 外部 API（LLM、MCP Server、真实 DB）一律 mock 或替换；Hify 自身从 Controller 到数据库走真实链路。
5. 测试数据按场景独立 `@Sql` 文件，不共享、不堆全局种子数据。
6. 所有断言用 AssertJ；错误码断言以生产代码实际定义为准，不以用户口述数字为准。

## Step 1: 读配置（动手前必做）

固定顺序，逐项确认后才能在清单里写技术前提：

1. `hify-app/pom.xml`：确认 `com.h2database:h2` 为 test scope，且已依赖全部业务模块。
2. `hify-app/src/test/resources/application-mock.yml`：
   - 确认 `spring.datasource.mysql` 为 H2 `MODE=MySQL` 内存库；
   - 确认是否存在第二个 `postgresql` 数据源（Hify 双库结构下必须存在，否则部分 Bean 起不来）；
   - 确认 Redis host/port/database、`spring.sql.init.schema-locations` 指向 `sql/schema-mock.sql`。
3. `hify-app/src/test/resources/sql/schema-mock.sql`：确认被测模块涉及的表是否已建；缺表则先补 H2 DDL 再规划。
4. `src/test/java/com/hify/integration/IntegrationTestBase.java`：确认基类能力（JWT token、Redis flush、mysqlDataSource、MockMvc）。
5. `src/test/java/com/hify/integration/support/MockProviderAdapter.java`：确认 mock profile 下的 LLM 替换实现，以及是否已支持可配置响应序列（Function Calling 场景需要扩展）。
6. 被测模块代码：Controller 路由、Service 事务/异步行为、Mapper SQL 是否带 `deleted=0`、错误码枚举。

如果配置缺失（例如 mock profile、H2、schema、MockProviderAdapter 不存在），先补齐最小设施，再继续规划。

## Step 2: 输出集成测试清单（不写代码）

按 `IT-<模块>-<序号>` 编号，分 P0/P1/P2 三档：

| IT 编号 | 优先级 | 场景 | 测试步骤 | 验证点 | 为什么这个优先级 |
|---------|--------|------|----------|--------|------------------|
| IT-AGENT-01 | P0 | 正常 CRUD | POST → GET → PUT → DELETE | 每步 body.code=200、DB 状态正确、软删除后列表不返回 | 核心链路，全系统依赖 |
| IT-AGENT-02 | P1 | 边界场景 | 重复名称 / 不存在 id | 业务错误码 | 主要功能 |
| IT-AGENT-03 | P2 | 边缘场景 | 超长字段 / 空 body | 不 500、有友好提示 | 有余力再做 |

优先级定义：

| 优先级 | 含义 | 必须满足 |
|--------|------|----------|
| P0 | 核心链路 | 必须覆盖，任何一次回归都必须跑 |
| P1 | 主要功能 | 应该覆盖，跟随迭代补齐 |
| P2 | 边缘场景 | 有余力再做，不阻塞交付 |

清单末尾必须标注：

- 哪些场景依赖当前不存在的设施（缺表、缺 MockProviderAdapter 响应序列、缺 @MockBean 类型），需要"先补设施再写测试"；
- 哪些场景会暴露已知 bug（先写红测试，见 Step 6）；
- 技术前提已确认/未确认项。

## Step 3: mock 策略决策表

| 依赖 | 策略 | 实现方式 | 示例 |
|------|------|----------|------|
| 真实 MySQL / PostgreSQL | 不连，用 H2 内存库 | `application-mock.yml` 双数据源，`MODE=MySQL` / `MODE=PostgreSQL`，`schema-mock.sql` 建表 | Provider CRUD、对话链路 |
| 真实 Redis | 不连，连本地 Redis db1，每测试前 flush | 基类 `stringRedisTemplate...flushDb()`；不可用时业务链路自动降级 | ChatContextCache |
| LLM API | mock profile 下替换适配器 | `@Component @Primary @Profile("mock")` 实现 `LlmProviderApi`，记录请求到 `CopyOnWriteArrayList`，提供 `getRequests()/clearRequests()` | 普通问答、多轮上下文、Function Calling |
| MCP Server | `@MockBean McpClientService` | 基类预留 `@MockBean`，测试内 stub `callTool()` 返回工具结果或抛异常 | 工具调用链路 |
| 定时任务 / 健康检查 | `@MockBean` 打桩 | 基类 `@MockBean ProviderHealthCheckScheduler` | 全部测试 |
| Hify 自身 Controller/Service/Mapper/DB | 不 mock | 真实启动，走完整链路 | 全部测试 |

决策原则：外部边界 mock，内部全链路真实。判断依据是"会不会产生网络 IO 或不可控外部副作用"。

## Step 4: 测试基类模板

标准配置固定如下，新增模块测试直接继承 `IntegrationTestBase`：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("mock")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // 注意：不能直接 @Autowired JdbcTemplate，
    // 会自动注入 postgresqlJdbcTemplate 连到空的 PG H2
    @Autowired
    @Qualifier("mysqlDataSource")
    protected DataSource mysqlDataSource;

    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected ProviderHealthCheckScheduler providerHealthCheckScheduler;

    protected String token;

    @BeforeEach
    void setUpTokenAndRedis() {
        token = JwtUtil.generate(1L, "integration", "USER");
        jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        try {
            stringRedisTemplate.getConnectionFactory().getConnection()
                    .serverCommands().flushDb();
        } catch (Exception ignored) {
            // Redis 不可用时缓存链路自动降级，不影响业务断言
        }
    }
}
```

测试类约束：

- 同步链路：类上加 `@Transactional @Rollback`，配合 `@Sql` 回滚；`@Sql` 文件放在 `src/test/resources/sql/<模块>/`，一个场景一个文件。
- 异步链路（SSE、llmExecutor 写库）：事务回滚不可靠，改用 `@AfterEach` 显式按依赖逆序清理，并向用户说明这一偏差。
- 请求体用 `objectMapper.writeValueAsBytes(...)` 而不是 `.content(String)`，避免中文乱码。
- 所有 HTTP 断言先 `status().isOk()`，再断言 `$.code`（Hify 业务错误码在 body.code，HTTP 恒 200）。
- JWT：`Authorization: Bearer <token>`，token 由基类生成。

## Step 5: 场景递进原则

从依赖最少、最容易跑通的场景开始，逐级推进：

1. 纯 CRUD（单表、同步、无外部依赖）→ Provider CRUD。
2. 普通 SSE 流（异步但无工具调用）→ 普通问答。
3. 需要拦截 LLM 请求的链路（多轮上下文、Function Calling）→ 先扩展 MockProviderAdapter。
4. 涉及权限 / 越权 / 工具失败的链路（需要 @MockBean 或现状锁定）→ 最后写。

每写完一个场景必须跑绿再写下一个。跑测试命令（PowerShell）：

```powershell
.\mvnw.cmd --% -pl hify-app -am test -Dtest=<测试类名列表,逗号分隔> -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -q
```

## Step 6: 已知 bug 的处理方式

1. 先在清单中标注"该场景对应已知 bug，预期先红"。
2. 先写测试，跑一次确认变红，并记录失败断言（这是证据，不是失败）。
3. 再修实现（SQL、排序、状态机等），修完跑绿。
4. 测试保留为回归锁，禁止删掉。
5. 如果排查后发现用户描述的 bug 不存在（例如实际代码路径与描述不符），如实汇报：不人为制造红测、不改正确的实现；把测试作为现状回归锁保留。

## 常见坑

| 现象 | 原因 | 处理 |
|------|------|------|
| `Table not found` 且指向 PG 库 | `@Autowired JdbcTemplate` 注入了 postgresqlJdbcTemplate | 基类改为 `@Qualifier("mysqlDataSource") DataSource` + `new JdbcTemplate(...)` |
| SSE 中文乱码 / 请求被转义 | `.content(String)` 编码问题 | `.content(objectMapper.writeValueAsBytes(...))` |
| `asyncDispatch` 拿不到 SSE 事件 | 没有先取异步结果 | `mvcResult.getAsyncResult(timeout)` 后再 `asyncDispatch(mvcResult)` |
| `@Transactional` 回滚后仍有脏数据 | 异步线程写库不受测试事务控制 | `@AfterEach` 按依赖逆序显式 DELETE |
| 用户口述错误码与实际不符（如 2001 vs 20001） | 口述与生产代码漂移 | 以生产 `ErrorCode` 枚举为准，并在汇报中说明 |
| Function Calling 场景固定返回 stop | MockProviderAdapter 未支持响应序列 | 扩展为可配置 `finishReason=tool_calls` + 第二轮最终回答 |
| 真实定时任务触发外部请求 | 健康检查等 Scheduler 在测试上下文运行 | 基类 `@MockBean` 打桩 |

## 汇报格式

每个阶段结束（清单确认、每批测试跑绿、bug 修复）汇报：

- 新增/修改文件清单；
- 跑绿测试名 + 验证命令；
- 与用户原始描述的偏差（数据隔离方式、错误码、bug 不存在等）；
- 下一步建议。

不要只报"完成"，要给出可复现的验证命令和实际结果。
