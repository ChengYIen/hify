# Hify 性能瓶颈分析

> **基准场景：** 50 人内部使用，典型操作为 Agent 对话（含 LLM 流式调用 + 工具调用）、知识库检索、工作流执行。
> **当前架构：** Spring Boot 2 副本 + MySQL 单实例 + Redis 单实例 + pgvector 单实例。

---

## 瓶颈总览

```
严重程度        瓶颈                      当前风险       一期处理？
────────────────────────────────────────────────────────────
🔴 致命    LLM API 调用链               必然遇到          ✅ 必须
🔴 致命    知识库文档解析               必然遇到          ✅ 必须
🟠 严重    线程池耗尽                   高峰必现          ✅ 必须
🟠 严重    数据库连接池耗尽             高峰必现          ✅ 必须
🟡 中等    pgvector 向量检索变慢        数据增长后触发     ⚠️ 预留
🟡 中等    Redis 单点故障               偶然              ⚠️ 预留
🟡 中等    JVM GC 停顿                  持续运行后触发     ⚠️ 预留
🟢 低      MySQL 单实例读压力           人数 × 5 后触发    ❌ 不做
🟢 低      带宽/网络延迟                特定操作触发       ❌ 不做
🟢 低      K8s Pod 启动慢               每次发布           ❌ 不做
```

---

## 🔴 致命级——必然遇到，不做系统不能用

### 瓶颈 1：LLM API 调用链

**症状：** 用户发一条消息，等了 30 秒才收到第一个字，Agent 循环转了三轮工具调用，总耗时 90 秒+。

**为什么是瓶颈：**

```
一次 Agent 对话的端到端耗时拆解:

  用户发消息
    ↓
  embedding 向量化（本地或调 API）        0.5s - 2s
    ↓
  RAG 检索（pgvector）                   0.1s - 1s
    ↓
  第 1 次 LLM 调用（思考 + 决定调工具）    3s - 30s   ← 最慢
    ↓
  第 1 次工具调用（调 HTTP API）          0.5s - 5s
    ↓
  第 2 次 LLM 调用（根据工具结果思考）     3s - 30s
    ↓
  第 3 次 LLM 调用（生成最终回复）         3s - 60s   ← 长回复更慢
    ↓
  SSE 流式输出到浏览器                    1s - 30s
  ─────────────────────────────────────
  总耗时:                                8s - 158s
```

**50 个用户同时对话的最坏情况：**
- 50 用户 × 平均 3 次 LLM 调用/对话 = 150 个并发 LLM 请求
- 每个 LLM 请求占一个 `llmExecutor` 线程（当前最大 100）
- **150 > 100 → 50 个请求在线程池外排队 → 用户体验雪崩**

**触发条件：** 10 人以上同时使用 Agent 功能。

**一期处理方案（4 件套，已在前一个方案中设计）：**

| 措施 | 效果 |
|---|---|
| ① 线程池隔离（已设计） | LLM 调用不阻塞 Tomcat，正常 API 请求不受影响 |
| ② 超时分级（已设计） | 单次 LLM 60s 超时、Agent 循环 90s 总超时，死等不会无限占线程 |
| ③ 重试 + 退避（已设计） | 偶发网络故障自动恢复，不消耗用户的重试耐心 |
| ④ 模型降级（已设计） | 主模型挂了自动切备选，不阻塞所有请求 |

**额外补充——降低 LLM 调用次数的优化：**

```java
// 优化 1：RAG 检索结果和对话上下文一起发，让 LLM 一次调用完成检索+回答
// 差的做法：先调 LLM 决定搜什么 → 搜 → 再调 LLM 生成回答（2 次调用）
// 好的做法：直接把检索结果注入 system prompt，1 次调用搞定

// 优化 2：工具调用并行化（如果工具之间没有依赖关系）
// 差的做法:  tool_1() → wait → tool_2() → wait → tool_3() → LLM
// 好的做法:  tool_1() ─┐
//           tool_2() ─┼─ parallel → LLM
//           tool_3() ─┘
```

---

### 瓶颈 2：知识库文档解析

**症状：** 用户上传一个 50MB 的 PDF，等了 2 分钟还没解析完，期间服务响应变慢。

**为什么是瓶颈：**

- PDF 解析（尤其含表格、扫描件的复杂 PDF）是 **CPU 密集型**操作
- 一个 50MB PDF 的解析 + 分段 + 向量化流程可能吃掉 1-2 个 CPU 核心几十秒
- 如果这个操作在 `llmExecutor` 或更糟——在 Tomcat 线程里跑，直接阻塞正常请求

**触发条件：** 首次上传较大的知识库文档（单个 PDF > 10MB）。

**一期处理方案：**

```java
// 文档解析必须用专门的线程池，不和 LLM 调用混在一起

// common/config/DocumentThreadPoolConfig.java
@Bean("documentExecutor")
public ThreadPoolTaskExecutor documentExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);        // 同时只解析 2 个文档
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(20);      // 最多排队 20 个
    executor.setThreadNamePrefix("doc-parse-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return executor;
}

// 解析流程：异步处理 + 进度回调
// 用户上传 → 返回"处理中" → 后台解析 → WebSocket/轮询通知完成
```

此外，文档上传接口必须限制文件大小：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 55MB
```

**一期是否处理：✅ 必须。** 不处理的话一个人上传大文档，其他人的对话全卡住。

---

## 🟠 严重级——高峰必现，但可以提前防住

### 瓶颈 3：线程池耗尽

**症状：** 高峰期用户提交请求后一直转圈，既不报错也不返回，最终超时。

**触发条件：** 同一时刻活跃的 LLM 调用 + 文档解析 + 工作流执行超过 `llmExecutor` 最大线程数（100）。

**为什么当前设计有缺口：**

```
当前线程池:
  llmExecutor:     core=8,  max=100, queue=0   → 突发时最多 100 个并发 LLM 调用
  documentExecutor: core=2, max=4,   queue=20  → 最多 24 个文档任务
  Tomcat:          default 200                  → 处理普通 REST 请求
  backgroundExecutor: core=2, max=4, queue=500 → 后台任务

缺口在哪:
  工作流执行没有自己的线程池。如果工作流节点里包含 LLM 调用，它会占 llmExecutor
  的线程。如果用户直接提交了一批工作流（比如每晚跑 20 个报表工作流），瞬间占满。
```

**一期处理方案：**

```java
// 方案 1：工作流用独立线程池
@Bean("workflowExecutor")
public ThreadPoolTaskExecutor workflowExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("workflow-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    // ↑ 工作流满了直接拒绝并报错，不要拖累其他功能
    return executor;
}

// 方案 2：所有线程池加监控
@Component
public class ThreadPoolMonitor {
    @Scheduled(fixedRate = 10_000)
    public void logPoolStats() {
        logPool("llmExecutor", llmExecutor);
        logPool("workflowExecutor", workflowExecutor);
        logPool("documentExecutor", documentExecutor);
    }

    private void logPool(String name, ThreadPoolTaskExecutor pool) {
        var stats = pool.getThreadPoolExecutor();
        log.info("{} pool: active={}, pool={}, queue={}, completed={}",
            name,
            stats.getActiveCount(),        // 正在执行
            stats.getPoolSize(),           // 当前线程数
            stats.getQueue().size(),       // 排队中
            stats.getCompletedTaskCount()  // 历史完成
        );
    }
}
```

**一期是否处理：✅ 必须。** 不监控线程池状态，出问题时完全不知道是哪个池子满了。

---

### 瓶颈 4：数据库连接池耗尽

**症状：** 数据库操作开始报 `HikariCP - Connection is not available, request timed out after 30000ms`。

**触发条件：** 某个操作拿了连接但不释放（比如在 `@Transactional` 方法里调了 LLM API），或者并发请求数超过可用连接数。

**HikariCP 默认配置：**

```
每个 backend Pod:
  maximumPoolSize: 10 (HikariCP 默认值，不是我们设的 20!)

2 个 Pod × 10 = 20 个连接

MySQL:
  max_connections: 151 (MySQL 默认值)
  20 个连接 < 151，MySQL 端没问题
```

**但真正的风险是这个：**

```java
// ❌ 危险写法：事务里调 LLM，连接被持有 30 秒+
@Transactional
public void handleMessage(MessageRequest request) {
    // 1. 保存用户消息（需要连接）
    messageMapper.insert(userMessage);       // 拿连接 1

    // 2. 调 LLM —— 耗时 30 秒！这期间连接一直被持有
    LlmResponseDTO response = llmProviderApi.chat(...);  // ← 连接没释放！

    // 3. 保存 AI 回复（需要连接）
    messageMapper.insert(aiMessage);         // 连接 1 还在，复用
}
// 连接 1 释放——但已经持有了 30 秒

// 如果 20 个请求同时进这个方法，20 个连接全占住 30 秒
// 第 21 个请求等 30 秒后超时报错
```

**一期处理方案：**

```java
// ✅ 正确写法：事务只包裹数据库操作，LLM 调用放事务外面

public void handleMessage(MessageRequest request) {
    // 1. 保存用户消息（独立事务，快速释放连接）
    messageService.saveUserMessage(userMessage); // 拿 → 写 → 放，毫秒级

    // 2. 调 LLM —— 不在任何事务里，不占数据库连接
    LlmResponseDTO response = llmProviderApi.chat(...); // 花 30 秒，不要紧

    // 3. 保存 AI 回复（独立事务）
    messageService.saveAiMessage(aiMessage); // 拿 → 写 → 放，毫秒级
}
```

**配置调整：**

```yaml
# application-prod.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 15         # 每个 Pod 15 个连接（2 Pod = 30）
      minimum-idle: 5               # 空闲保持 5 个
      connection-timeout: 10000     # 10 秒拿不到连接就报错，不要等 30 秒
      idle-timeout: 300000          # 空闲 5 分钟回收
      max-lifetime: 600000          # 最多存活 10 分钟
      leak-detection-threshold: 5000  # 连接持有超过 5 秒打印警告日志
```

**加监控：**

```java
// common/config/HikariMonitor.java
@EventListener
public void onConnectionLeak(HikariPool.ConnectionLeakEvent event) {
    log.error("HIKARI LEAK: connection held for {}ms, stack: {}",
              event.getLeakDetectionThreshold(),
              event.getSource());
}
```

**一期是否处理：✅ 必须。** 这是最容易犯的错——在事务里调 LLM——一旦犯了一次，高峰期直接打挂数据库连接池。`leak-detection-threshold: 5000` 是你最重要的报警。

---

## 🟡 中等级——会慢慢变慢，但不会一下崩

### 瓶颈 5：pgvector 向量检索变慢

**触发条件：** 向量数据超过 5 万条后，IVFFlat 索引的召回精度和速度开始下降。

```
数据量级           IVFFlat 检索时间         是否需要处理
─────────────────────────────────────────────────
1 万条             < 10ms                  ❌ 不用
5 万条             10ms - 50ms             ⚠️ 开始关注
10 万条            50ms - 200ms            ⚠️ 该换 HNSW 索引了
50 万条            200ms - 1s+             🔴 必须处理
```

**一期处理：**

- 先用 IVFFlat，不做提前优化
- 在 Service 层给检索操作打点：`meterRegistry.timer("hify.rag.search").record(...)`
- 当 P99 检索时间超过 300ms 时，切换 HNSW 索引：

```sql
-- 切换脚本（会锁表，选低峰期执行）
DROP INDEX IF EXISTS idx_document_vectors_ivfflat;
CREATE INDEX idx_document_vectors_hnsw
  ON document_vectors USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 200);
```

**一期是否处理：❌ 不做。** 留好监控打点即可，5 万条之前不会有问题。提前优化只会浪费时间去调一个你还用不到的 HNSW 参数。

---

### 瓶颈 6：Redis 单点故障

**触发条件：** Redis Pod 挂了、OOM 了、或者网络暂时不可达。

**影响面：**

```
Redis 挂了 → 以下功能同时受影响:
  ✗ Session 共享        → 用户被踢出登录
  ✗ LLM 结果缓存        → 降级为每次调 API（速度慢但功能正常）
  ✗ Agent 任务队列      → 异步任务丢失
```

**严重程度判断：** 内部工具，Redis 挂了 K8s 会在 30 秒内自动重启 Pod，AOF 持久化保证数据不丢（丢最后 1 秒）。最坏情况是 30 秒内用户要重新登录 + 正在排队的异步 Agent 任务丢失。

**一期处理：**

```java
// 对 Redis 依赖做降级处理，不要因为 Redis 挂了就完全不可用

// Session 降级：Redis 不可用时 fallback 到内存 Session
// 代价是用户可能被路由到另一个 Pod 时丢失登录状态，但至少用户知道"出问题了"
// 而不是白屏

// 缓存降级：Redis 挂了就不读缓存，直接调 LLM API
// 代码上就是 try-catch 并跳过缓存逻辑

// 队列降级：Redis 队列不可用时拒绝创建异步任务，提示用户稍后再试
// 而不是让任务静默丢失
```

**一期是否处理：⚠️ 预留降级代码，不搞 Redis Sentinel/Cluster。** 50 人内部工具不需要 Redis 高可用集群，但代码里对 Redis 异常必须兜底——不能因为缓存不可用就导致业务报 500。

---

### 瓶颈 7：JVM GC 停顿

**触发条件：** 堆内存持续增长（对话历史缓存、大文档解析、Agent 循环中的临时对象），触发 Full GC，停顿 2-5 秒。

**为什么是问题：** 2-5 秒的停顿对 API 请求来说意味着用户看到转圈。对 SSE 流式连接来说，停顿期间没有 chunk 发出，用户以为服务卡死了。

**一期处理：**

```dockerfile
# JVM 参数优化
ENTRYPOINT ["java",
    "-Xms512m", "-Xmx2g",
    "-XX:+UseG1GC",                          # G1 垃圾回收器，适合 2GB 堆
    "-XX:MaxGCPauseMillis=200",              # 目标停顿 < 200ms
    "-XX:G1HeapRegionSize=4m",              # Region 大小
    "-XX:+HeapDumpOnOutOfMemoryError",       # OOM 时自动 dump
    "-XX:HeapDumpPath=/tmp/heapdump.hprof",
    "-XX:+ExitOnOutOfMemoryError",           # OOM 后直接死，让 K8s 重启
    "-jar", "/app/hify-app.jar"
]
```

**关键参数解释：**

| 参数 | 为什么 |
|---|---|
| `-XX:+UseG1GC` | G1 对 2GB 堆的停顿控制比 Parallel GC 好得多，Full GC 概率更低 |
| `-XX:MaxGCPauseMillis=200` | 告诉 G1 尽量把停顿控制在 200ms 以内——SSE 流短暂卡顿用户几乎无感知 |
| `-XX:+ExitOnOutOfMemoryError` | OOM 了主动退出，让 K8s 重启 Pod，比半死不活强 |

**一期是否处理：⚠️ 配置到位即可，不做深度 GC 调优。** G1 + 200ms 停顿目标已经是最好的默认值。更深度的调优需要基于真实的 GC 日志，现在没数据没法调。

---

## 🟢 低优先级——离你还很远

### 瓶颈 8：MySQL 单实例读压力

**触发条件：** 用户数 × 10，且每个请求都要查大量对话历史/Agent 配置。MySQL 开始出现慢查询堆积。

**一期处理：❌ 不做。** 当前 QPS < 100，MySQL 单实例轻松扛。等慢查询日志里出现明确瓶颈了再加索引或从库。

---

### 瓶颈 9：带宽 / 网络延迟

**触发条件：** 文件下载/上传场景。比如对话中传一个 50MB 的附件，或者前端加载一个 5MB 的 JS bundle。

**一期处理：❌ 不做。** 内网带宽不是瓶颈。前端打包时已经做了 code splitting，首次加载 < 1MB。

---

### 瓶颈 10：K8s Pod 启动慢

**触发条件：** JVM 启动 + Spring 上下文初始化 + 数据库连接池预热，大约 20-40 秒。

**影响：** 滚动更新时有短暂的服务容量下降（2 个 Pod 变 1 个），但这 20 秒内 50 个人的请求量 1 个 Pod 也扛得住。

**一期处理：❌ 不做。** 50 人场景不需要 Native Image 编译或者 Spring AOT 来压启动时间。

---

## 一期必须做的清单

```
□ 1. LLM 调用韧性四件套（线程池隔离 / 超时 / 重试 / 熔断）
□ 2. 文档解析专用线程池 + 异步处理 + 文件大小限制
□ 3. 工作流独立线程池 + 所有线程池 Metrics 打点
□ 4. 数据库连接池：事务不包裹 LLM 调用 + leak-detection-threshold
□ 5. Redis 降级逻辑：缓存不可用时正常走 API
□ 6. JVM 参数：G1GC + MaxGCPauseMillis=200
```

六件事加起来大约 2-3 个工作日的代码量，但能在上线第一天就防住 90% 的性能事故。

---

> **核心原则：50 人场景下，性能瓶颈不在你的代码或数据库，而在你控制不了的外部 LLM API。先把那层的韧性做好，内部的瓶颈远没到需要提前优化的程度。**
