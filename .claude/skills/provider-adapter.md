# Skill: 新增 Provider Adapter

触发方式：当用户说"接入新供应商"、"新增 XX 提供商支持"、"加一个 Adapter" 时按此流程推进。

---

## 背景

Provider 的连通性测试、模型同步、调用逻辑按供应商类型有差异。
最初用 switch-case 实现，后来重构为策略模式：

```
ProviderAdapterFactory
  └── Map<ProviderType, ProviderAdapter>
        ├── OpenAiAdapter        (OPENAI / OPENAI_COMPATIBLE / DEEPSEEK)
        ├── AnthropicAdapter     (ANTHROPIC)
        ├── AzureOpenAiAdapter   (AZURE_OPENAI)
        └── OllamaAdapter        (OLLAMA)
```  

每个 Adapter 实现统一接口，Factory 按类型路由，新增供应商只需加一个 Adapter 类 + 注册，不改任何已有代码。

---

## Step 1 — 分析目标供应商 API

**目标**：搞清楚接入该供应商需要哪些差异化实现。

需要调研的问题（逐一回答）：

| 问题 | 说明 |
|------|------|
| 认证方式 | Bearer Token / API Key Header / 双 Header / 无认证？ |
| 列模型接口 | URL 路径？返回结构（`data[]` / `models[]` / 其他）？ |
| 必填 authConfig 字段 | 如 `apiKey`、`apiVersion`、`anthropicVersion` |
| baseUrl 默认值 | 官方默认是什么？用户可否自定义？ |
| 特殊请求头 | 如 Anthropic 的 `anthropic-version` |
| Chat 调用路径 | `/v1/chat/completions` 还是其他？ |
| 流式响应格式 | SSE `data: {...}` 标准格式，还是自定义格式？ |

**产出物**：一份简短的 API 特征说明（口头或注释均可）

> ⚠️ **等待用户确认**：API 特征分析结果确认后再写代码

---

## Step 2 — 实现 Adapter

**目标**：新建一个实现 `ProviderAdapter` 接口的类。

**接口定义**（位于 `hify-provider/.../adapter/ProviderAdapter.java`）：

```java
public interface ProviderAdapter {
    /** 该Adapter支持的供应商类型（可多个） */
    List<String> supportedTypes();

    /** 连通性测试，返回延迟和模型数 */
    ConnectionTestResult test(Provider provider, OkHttpClient testClient);

    /** 拉取并返回模型列表（用于同步） */
    List<String> listModels(Provider provider, OkHttpClient client);

    /** 构造chat请求体（流式） */
    RequestBody buildChatRequest(Provider provider, List<ChatMessage> messages);

    /** 解析流式响应的一行delta文本，无内容返回null */
    String parseDelta(String line);
}
```

**文件位置**：`hify-provider/src/main/java/com/hify/provider/adapter/impl/XxxAdapter.java`

**实现模板**：

```java
@Component
public class XxxAdapter implements ProviderAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public List<String> supportedTypes() {
        return List.of("XXX");
    }

    @Override
    public ConnectionTestResult test(Provider provider, OkHttpClient testClient) {
        long start = System.currentTimeMillis();
        try {
            String apiKey = getAuth(provider, "apiKey");
            String url = provider.getBaseUrl().stripTrailing() + "/v1/models";
            Map<String, String> headers = Map.of("Authorization", "Bearer " + apiKey);

            String body = llmHttpClient.get(url, headers, testClient);
            int latency = (int) (System.currentTimeMillis() - start);
            int modelCount = parseDataArraySize(body);
            return ConnectionTestResult.ok(latency, modelCount);
        } catch (LlmApiException e) {
            return ConnectionTestResult.fail(e.getMessage());
        } catch (Exception e) {
            return ConnectionTestResult.fail("测试异常：" + e.getMessage());
        }
    }

    // ... 其他方法
}
```

**注意事项**：
- `getAuth(provider, key)` 找不到字段时抛 `IllegalArgumentException("authConfig 缺少字段：" + key)`，会被上层统一捕获，不要吞掉
- 解析模型列表时不同供应商返回字段不同：OpenAI 是 `data[].id`，Ollama 是 `models[].name`，Anthropic 是 `data[].id`
- 流式解析：OpenAI 格式每行是 `data: {...}`，遇到 `data: [DONE]` 停止；Anthropic 是 `data: {"type":"content_block_delta",...}`
- 有特殊 Header 的（如 Anthropic `anthropic-version`）放在 authConfig 里，不要硬编码版本号

**验证**：
```bash
mvn clean install -DskipTests -pl hify-provider -am
```

---

## Step 3 — 注册到 Factory

**目标**：让 Factory 能路由到新 Adapter。

**Factory 实现**（位于 `hify-provider/.../adapter/ProviderAdapterFactory.java`）：

```java
@Component
public class ProviderAdapterFactory {

    private final Map<String, ProviderAdapter> adapterMap;

    // Spring自动注入所有ProviderAdapter实现
    public ProviderAdapterFactory(List<ProviderAdapter> adapters) {
        this.adapterMap = new HashMap<>();
        for (ProviderAdapter adapter : adapters) {
            for (String type : adapter.supportedTypes()) {
                adapterMap.put(type.toUpperCase(), adapter);
            }
        }
    }

    public ProviderAdapter get(String type) {
        ProviderAdapter adapter = adapterMap.get(type.toUpperCase());
        if (adapter == null) {
            throw new BizException(ErrorCode.PROVIDER_TYPE_NOT_SUPPORTED);
        }
        return adapter;
    }
}
```

**注册方式**：新 Adapter 加 `@Component` 注解，`supportedTypes()` 返回对应的类型字符串，Factory 在启动时自动扫描注册，**无需手动修改 Factory 代码**。

**验证**：启动后在日志里确认 adapterMap 包含新类型（可在 Factory 构造方法加一行 log）。

---

## Step 4 — 更新 ProviderType 枚举（如需要）

如果新供应商需要在前端下拉菜单里出现，同步更新：

- 后端：`hify-provider/.../constant/ProviderType.java`（如果有枚举）
- 前端：`hify-web/src/views/provider/ProviderList.vue` 的 `providerTypes` 数组
- 数据库：`provider.type` 是 varchar，无需迁移，直接用新字符串值

---

## Step 5 — 验证

### 后端 curl 验证
```bash
# 1. 创建新供应商
curl -s -X POST http://localhost:8080/api/v1/providers \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "测试-XXX",
    "type": "XXX",
    "baseUrl": "https://api.xxx.com",
    "authConfig": { "apiKey": "sk-test-xxx" }
  }' | jq .

# 2. 连通性测试（id替换为上一步返回的id）
curl -s -X POST http://localhost:8080/api/v1/providers/1/test-connection | jq .
```

**预期**：
- 真实 key：`success: true`，有 `latencyMs` 和 `modelCount`
- 假 key：`success: false`，`errorMessage` 包含"无效"或"认证失败"，**不能是 500**

### 浏览器验证
1. 前端下拉能选到新类型
2. 创建后列表显示正常
3. 点"测试"按钮有结果提示

---

## 常见坑

| 现象 | 原因 | 修复 |
|------|------|------|
| Factory 找不到新 Adapter | 忘加 `@Component` 或 `supportedTypes()` 返回值大小写不一致 | Factory 用 `toUpperCase()` 统一，Adapter 返回值也大写 |
| authConfig 字段缺失导致 500 | 前端创建时没传必填的 auth 字段 | `getAuth()` 的异常信息要明确说缺哪个字段 |
| 连通性测试超时 | 用了默认 OkHttpClient（无超时限制） | 必须用注入的 `testClient`（10s 超时），不要 new |
| 模型数量永远是 0 | 响应结构解析错误（字段名不是 `data`） | 用 `objectMapper.readTree(body)` 打印原始结构再解析 |
| 流式响应乱码/截断 | Anthropic 等有自己的 SSE 事件类型，直接用 OpenAI 解析逻辑会漏掉 | `parseDelta()` 按各供应商格式单独实现 |
