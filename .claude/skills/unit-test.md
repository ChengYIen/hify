---
name: unit-test
description: 为任意 Service 方法编写单元测试的固定流程。当用户输入 /单测 后直接跟目标方法（如 /单测AgentService.createAgent）、或要求"给 XxxService 写单测/单元测试/测试计划"时自动启用。流程包括读代码、输出待确认的测试计划、主动检查计划的技术问题、Service 逻辑与 DTO 约束分文件测试、运行测试与失败归因。
---

# Service 单元测试流程（/单测）

## 触发方式

- 用户输入 `/单测<目标>`，例如 `/单测AgentService.createAgent`、`/单测RefundService.submitRefund`，中间不加空格。
- 用户说"给 XxxService 写单元测试""分析这个方法并给测试计划"等语义时自动启用。
- 对任何 Service 方法都直接按本流程执行，不需要用户重复说明步骤。

## 铁律

1. 先输出测试计划，等用户确认后再写代码。
2. 计划阶段不写测试代码，也不写实现代码。
3. 计划依赖的行为如果当前实现不存在（错误码、校验规则、默认值），必须在计划中标注"需补实现"。
4. 主动审查计划的技术可行性：mock 方式、Bean Validation、Spring 上下文、错误码是否已定义，发现问题当场调整，不等用户发现。
5. Service 业务逻辑测试与 DTO 约束测试必须分文件。
6. 测试失败先归因：是测试写错、实现 bug，还是测试环境问题；不能盲目改测试或改实现。

## Step 1: 读代码

固定顺序，每读完一个对象只输出"文件:行号 + 一句话职责"，不要复述整段代码。

1. 被测 Service 实现类（ServiceImpl 或 Service）：
   - 目标方法完整实现与 public 契约
   - 类级注解：`@Transactional`、`@Cacheable/@CacheEvict`、`@Validated`、`@Service`
   - 构造器依赖清单：Mapper、其他 Service、外部 API、Redis 等
2. 依赖的 DTO / Request：
   - 校验注解：`@NotBlank`、`@Size`、`@Min/@Max`、`@Pattern`、`@Valid` 级联
   - 可选字段与默认值语义
3. `ErrorCode` / `BizException`：
   - 方法可能抛的错误码是否已定义
   - 错误消息是否泄露敏感信息（如 apiKey）
4. 实体 / Mapper / Repository：
   - 表字段约束：长度、唯一键、逻辑删除
   - Mapper 方法语义：`insert` 是否回填 id、`selectCount` 是否自动带 `deleted=0`
5. Controller（可选）：确认入参校验是否已在 Controller 做，判断 Service 层是否还需要校验。

## Step 2: 输出测试计划（不写代码）

计划固定包含三块内容。

### 2.1 执行路径树

用文本树标注正常与异常路径，每条路径注明关键变量和分支条件：

```text
createAgent(CreateAgentRequest)
├── N1 正常：参数合法 -> 查重 -> 组装实体 -> insert -> 返回 response
│   └── 子分支：可选字段为空 -> 默认值
├── E1 名称重复：selectCount>0 -> BizException(AGENT_NAME_DUPLICATE)
├── E2 入参校验失败：name=null -> ConstraintViolationException
└── E3 外部依赖失败：模型不存在 -> BizException(PROVIDER_NOT_FOUND)
```

### 2.2 边界条件表

至少覆盖这些类别：

| 边界 | 风险 | 预期行为 |
|------|------|----------|
| null / 空串 / 空白 | 校验漏掉或 NPE | 明确抛什么异常 |
| 字段长度上下界 | DB 报错代替 400 | DTO 层拦截或明确错误码 |
| 唯一约束 / 并发竞态 | check-then-act 竞态 | 业务错误码 + 数据库兜底 |
| 默认值 | priority 等为空 | 明确默认值 |
| 异常回滚 | 部分写入 | 事务边界是否该验证 |
| 逻辑删除 | 同名复用 | 查询是否排除已删除行 |
| 敏感字段 | apiKey 回显 | 响应不得包含密钥 |

### 2.3 分优先级场景表

按 P0 / P1 / P2 输出场景，测试方法名遵循 `should_[期望结果]_when_[输入条件]`：

| 优先级 | 场景 | 测试方法名 | 断言要点 | 所属文件 |
|--------|------|------------|----------|----------|
| P0 | 正常创建 | `should_createAgent_whenNameUnique` | 返回 id、insert 一次、字段正确 | Service 测试 |
| P0 | 名称重复 | `should_throwAgentNameDuplicate_whenNameExists` | 错误码、insert 未调用 | Service 测试 |
| P1 | name 为 null | `should_throwConstraintViolation_whenNameIsNull` | ConstraintViolationException | DTO 约束测试 |
| P1 | 外部依赖失败 | `should_throwProviderNotFound_whenModelMissing` | 错误码、未触达 DB 写入 | Service 测试 |

场景表最后要标注：哪些场景依赖当前不存在的实现，需要"先补实现再写测试"。

## Step 3: 写代码前的技术确认清单

逐项确认，缺一项就停下来补齐再写：

- [ ] mock 方式：项目现有约定是 MockitoExtension + `@Mock` + `@InjectMocks`；若用户指定 `@MockBean`，需搭最小 Spring 测试上下文，并补齐缓存/事务/校验基础设施。
- [ ] 断言库：AssertJ，禁用 JUnit 的 `assertTrue` / `assertEquals`。
- [ ] Bean Validation 场景：
  - Service 层需要抛 `ConstraintViolationException` 时，检查 `@Validated`（类级）+ `@Valid`（参数）是否同时存在于接口与实现，否则出现 HV000151。
  - 确认 `MethodValidationPostProcessor` / Validator 是否已在测试上下文注册。
- [ ] Spring 注解依赖：`@Transactional` 需要事务管理器；`@CacheEvict` 需要 CacheManager + `@EnableCaching`；Spring 6 已移除 `ResourcelessTransactionManager`，不要使用。
- [ ] Mapper mock 行为：`insert` 是否回填 id（用 `thenAnswer` 模拟）、`selectCount` 返回类型、是否带逻辑删除条件。
- [ ] 错误码：计划用到的 ErrorCode 是否已存在；不存在则标记"需新增"。
- [ ] 禁止项：不写集成测试、不连真实 DB/Redis/HTTP、不 mock 被测类私有方法。
- [ ] 运行命令：确认目标模块的 Maven 命令（如 `.\mvnw.cmd -pl hify-module-agent -am test -q`）。

## Step 4: 测试文件拆分原则

- `XxxServiceImplTest.java`：Service 业务逻辑。测正常路径、异常路径、默认值、Mapper 交互次数与参数捕获。
- `XxxCreateRequestValidationTest.java`（或 DTO 层测试）：DTO 约束规则。用 Validator 直接校验或 MockMvc 验证参数校验。
- 一个 Service 方法同时有业务分支和 DTO 约束时，两个文件都建，各测各的职责。
- 简单 CRUD Controller 的 `@Valid` + 转发不写单测，按项目规范归集成测试。

## Step 5: 跑测试与失败归因

1. 运行模块测试：
   ```powershell
   .\mvnw.cmd -pl <module> -am test -q
   ```
2. 编译失败：先查是否引用了不存在的 API / 错误码 / 依赖；若是实现缺失，回到 Step 3 标记的"需补实现"项。
3. 断言失败：从失败断言定位到具体路径，先判断期望是否正确（计划阶段是否错误假设），再判断实现是否符合计划。
4. 分类处理：
   - 测试写错（stub 错误、断言过强/过弱、命名不规范）-> 改测试。
   - 实现 bug（缺错误码、缺校验、默认值不对、事务/缓存行为不符）-> 修实现，测试保留为回归。
   - 测试环境问题（上下文启动失败、缺 Bean）-> 补最小测试配置，不引入集成测试。
5. 全绿后确认 surefire 报告：`Tests run: N, Failures: 0, Errors: 0`，再汇报改动与验证命令。

## 常见坑

| 现象 | 原因 | 处理 |
|------|------|------|
| HV000151 约束覆盖异常 | 接口与实现的 `@Valid` 不一致 | 接口和实现参数都加 `@Valid` |
| Spring 6 找不到 `ResourcelessTransactionManager` | 该类已移除 | 单测不验证事务，去掉 `@EnableTransactionManagement`，事务行为留给集成测试 |
| `ConstraintViolationException` 未触发 | 缺 `@Validated`、`@Valid` 或校验处理器 | 补全类级 `@Validated` + 参数 `@Valid` + 测试上下文校验 Bean |
| Mapper mock 后响应 id 为 null | 实际 insert 由 MyBatis 回填 id，mock 不模拟 | 用 `thenAnswer` 设置 id，再断言 response.id |
| 计划要求错误码不存在 | 测试计划超前于实现 | 先补 ErrorCode 与实现，再写对应测试 |
