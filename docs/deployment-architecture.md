# Hify 部署架构设计 v1.0

> **阶段：** MVP 上线，50 人内部使用。
> **技术栈：** Spring Boot + Vue + MySQL + Redis + pgvector。
> **容器编排：** Docker + K8s（本地集群）。

---

## 一、组件全景

一共 6 类 Pod，全部跑在 K8s 集群里：

```
                        ┌─────────────────────────────────────────────┐
                        │                  K8s 集群                    │
                        │                                             │
   用户浏览器             │   ┌──────────┐    ┌──────────────────┐     │
   (Vue SPA)  ───────────────▶│  Ingress │───▶│  hify-frontend   │     │
                        │   │ (Nginx)  │    │  (Nginx 静态文件)  │     │
                        │   └────┬─────┘    └──────────────────┘     │
                        │        │                                    │
                        │        │ /api/*                             │
                        │        ▼                                    │
                        │   ┌──────────────────────────────────┐     │
                        │   │         hify-backend              │     │
                        │   │    (Spring Boot 单体 JAR)         │     │
                        │   │    副本数: 2   资源: 512Mi-2Gi    │     │
                        │   └──┬──────────┬──────────┬─────────┘     │
                        │      │          │          │               │
                        │      ▼          ▼          ▼               │
                        │   ┌──────┐ ┌──────┐ ┌──────────┐          │
                        │   │MySQL │ │Redis │ │PostgreSQL│          │
                        │   │ 8.0  │ │ 7.x  │ │+pgvector │          │
                        │   │单实例 │ │单实例 │ │ 单实例   │          │
                        │   └──────┘ └──────┘ └──────────┘          │
                        │                                             │
                        └─────────────────────────────────────────────┘
```

---

## 二、组件职责表

| 组件 | 镜像 | 副本数 | 职责 | 为什么是这个数 |
|---|---|---|---|---|
| **hify-frontend** | `nginx:alpine` + Vue 构建产物 | 1 | 托管前端静态文件（HTML/JS/CSS），直接响应用户浏览器请求 | 前端是无状态静态文件，1 个 Pod 足够，挂了 K8s 自动拉起来 |
| **hify-backend** | 自建 `Dockerfile` 打 Spring Boot JAR | 2 | 全部业务逻辑：Agent 编排、对话引擎、RAG 检索、工作流执行、调 LLM API | 2 个保证滚动更新不中断服务，挂一个另一个扛住 |
| **MySQL** | `mysql:8.0` | 1 | 持久化业务数据：Agent 配置、对话历史、工作流定义、用户信息 | 50 人场景单实例完全够，后续可加从库做读写分离 |
| **Redis** | `redis:7-alpine` | 1 | ① Session 共享（后端多 Pod 时保持登录状态）② Agent 任务消息队列 ③ LLM 调用结果的短时缓存 | 50 人场景单实例内存 256Mi 够用 |
| **PostgreSQL + pgvector** | `pgvector/pgvector:pg16` | 1 | 知识库 RAG 的向量存储：存 Embedding 向量 + 文档元数据，跑向量相似度检索 | RAG 场景单表几十万向量内单实例轻松扛 |
| **Ingress** | `nginx-ingress-controller` | 1 | 统一流量入口：路由分发（`/api/*` → backend，`/` → frontend）、TLS 终止 | K8s 集群标配 |

---

## 三、请求流转全景

两台机器的小集群为例（node-1 和 node-2）：

```
┌──────────────────────────────────────────────────────────────────────────┐
│  用户 A 浏览器                   用户 B 浏览器                             │
│  192.168.1.100                  192.168.1.101                            │
└────────┬──────────────────────────────┬───────────────────────────────────┘
         │                              │
         │  https://hify.internal      │
         │      /api/v1/chat           │
         │                              │
         ▼                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Ingress (Nginx)                                   │
│                    10.96.0.1  (ClusterIP → NodePort)                      │
│                                                                          │
│  规则:                                                                    │
│    /api/*          → hify-backend-svc:8080                               │
│    /               → hify-frontend-svc:80                                │
│                                                                          │
│  TLS: cert-manager 自动申请/续签 或 内部自签证书                            │
└──────────────────────────────────────────────────────────────────────────┘
         │                              │
         │ /                           │ /api/*
         ▼                              ▼
┌─────────────────────┐   ┌─────────────────────────────────────────────┐
│ hify-frontend       │   │               hify-backend                   │
│ (Nginx 静态托管)     │   │                                             │
│                     │   │  请求 1:  POST /api/v1/chat                  │
│  /index.html ──▶ Vue│   │  ┌──────────────────────────────────────┐   │
│  SPA 加载到浏览器    │   │  │ 1. 参数校验 (Controller)              │   │
│                     │   │  │ 2. 查 Agent 配置 ────▶ MySQL         │   │
│ Vue 前端           │   │  │ 3. 查对话历史   ────▶ MySQL         │   │
│ 调 /api/*          │   │  │ 4. 调 LLM API   ────▶ 外部 OpenAI    │   │
│ 都是到 Ingress      │   │  │       ↓                                │   │
│ 再路由到 backend    │   │  │    返回 SSE 流 ────▶ 浏览器（逐 chunk）│   │
│                     │   │  │ 5. 保存消息     ────▶ MySQL         │   │
│                     │   │  │ 6. 写缓存       ────▶ Redis         │   │
│                     │   │  └──────────────────────────────────────┘   │
│                     │   │                                             │
│                     │   │  请求 2:  POST /api/v1/knowledge/search      │
│                     │   │  ┌──────────────────────────────────────┐   │
│                     │   │  │ 1. 文本向量化 (Embedding)              │   │
│                     │   │  │ 2. 向量检索  ────▶ PostgreSQL+pgvector│   │
│                     │   │  │ 3. 重排 + 返回结果                     │   │
│                     │   │  └──────────────────────────────────────┘   │
│                     │   │                                             │
│                     │   │  请求 3:  POST /api/v1/workflow/execute      │
│                     │   │  ┌──────────────────────────────────────┐   │
│                     │   │  │ 1. 加载工作流定义 ────▶ MySQL         │   │
│                     │   │  │ 2. 顺序执行节点                       │   │
│                     │   │  │ 3. LLM 节点 → 调外部 API              │   │
│                     │   │  │ 4. 状态暂存  ────▶ Redis             │   │
│                     │   │  └──────────────────────────────────────┘   │
│                     │   │                                             │
│                     │   │  Pod 分布:                                   │
│                     │   │    node-1: backend-pod-1                    │
│                     │   │    node-2: backend-pod-2                    │
│                     │   │  Service 负载均衡: 请求随机打到两个 Pod       │
│                     └───┴─────────────────────────────────────────────┘
                                  │          │          │
                                  ▼          ▼          ▼
                           ┌──────────┐ ┌──────┐ ┌──────────────┐
                           │  MySQL   │ │Redis │ │PostgreSQL    │
                           │  (主库)   │ │      │ │+ pgvector    │
                           │          │ │      │ │              │
                           │ 存什么:   │ │ 存什么:│ │ 存什么:      │
                           │ - 用户    │ │- 会话 │ │ - 向量嵌入   │
                           │ - Agent  │ │- 缓存 │ │ - 文档元数据 │
                           │ - 对话    │ │- 队列 │ │ - 检索索引   │
                           │ - 工作流  │ │- 限流 │ │              │
                           └──────────┘ └──────┘ └──────────────┘
```

---

## 四、每个组件详细说明

### 4.1 hify-frontend

```
容器内部:
  /usr/share/nginx/html/
  ├── index.html
  ├── assets/
  │   ├── index-abc123.js    ← Vue 打包产物
  │   └── index-abc123.css
  └── nginx.conf              ← 自定义配置
```

Nginx 配置需要处理 Vue Router 的 history mode：

```nginx
server {
    listen 80;

    # Vue 静态文件
    location / {
        root   /usr/share/nginx/html;
        index  index.html;
        try_files $uri $uri/ /index.html;  # history mode fallback
    }

    # 健康检查给 K8s 用
    location /health {
        return 200 "ok";
        add_header Content-Type text/plain;
    }
}
```

**为什么不做 SSR：** 50 人内部工具，首屏加载多 0.5 秒没人投诉。不做 SSR 省掉一个 Node.js 服务。

### 4.2 hify-backend

Spring Boot 的 Dockerfile：

```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S hify && adduser -S hify -G hify
USER hify
COPY target/hify-app.jar /app/hify-app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "/app/hify-app.jar"]
```

K8s 资源配置（50 人足够）：

```yaml
resources:
  requests:
    cpu: "500m"
    memory: "512Mi"
  limits:
    cpu: "2000m"
    memory: "2Gi"

# HPA（可选，初期不配也行）
# minReplicas: 2, maxReplicas: 4, targetCPU: 70%
```

需要暴露的 Actuator 端点：

```yaml
# application-prod.yml
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,prometheus
  endpoint:
    health:
      probes:
        enabled: true    # /actuator/health/readiness, /actuator/health/liveness
```

| 探针 | 路径 | 用途 |
|---|---|---|
| Liveness | `/actuator/health/liveness` | Pod 是否还活着，挂了 K8s 重启 |
| Readiness | `/actuator/health/readiness` | Pod 能否接收流量，数据库连不上时标 NOT_READY，K8s 暂停路由 |
| Startup | `/actuator/health` | 启动完成标志，防止请求打到正在初始化的 Pod |

Prometheus 抓取配置：

```yaml
scrape_configs:
  - job_name: hify
    static_configs:
      - targets: ['hify-backend:8081']
```

### 4.3 MySQL

| 配置项 | 值 | 理由 |
|---|---|---|
| 版本 | 8.0 | 长期支持，稳定 |
| 存储 | 100Gi PersistentVolume | 对话记录会持续增长，预留空间 |
| 副本 | 1（主库，无只读副本） | 50 人场景读压力小，主库扛得住 |
| 备份 | CronJob 每日凌晨 mysqldump → 对象存储/NFS | 数据是核心资产，必须备份 |
| 连接池 | HikariCP 默认（最大 20/实例） | 2 个 Pod × 20 = 40 并发连接，远超 50 人需要的实际 DB 并发 |

**为什么不搞主从：** 50 人场景下主从读写分离的收益几乎为零——单库 QPS 不超过 50，MySQL 单实例能跑几千 QPS。备份比高可用更优先。

### 4.4 Redis

| 配置项 | 值 | 理由 |
|---|---|---|
| 版本 | 7.x Alpine | 更小更快 |
| 内存 | 256Mi | Session + 缓存 + 轻量队列，256Mi 很宽裕 |
| 持久化 | AOF，每秒 fsync | 丢失 1 秒数据可接受，比 RDB 快照安全 |
| 副本 | 1（无 Sentinel） | 挂了自动重启即可，不搞高可用 |

**Redis 的三种用途：**

```
用途 1 — Session 共享:
  用户 A 第一次请求打到 backend-pod-1 → Session 写入 Redis
  用户 A 第二次请求打到 backend-pod-2 → 从 Redis 读到 Session → 不用重新登录

用途 2 — LLM 结果短时缓存:
  相同 prompt + 相同参数 → 5 分钟内返回缓存结果
  Key: llm:cache:{md5(prompt+messages+tools)}  TTL: 300s

用途 3 — Agent 任务消息队列 (Redis List):
  用户提交 Agent 任务 → LPUSH 到队列
  @Scheduled 消费者 BRPOP 取任务 → 异步执行
  这是轻量版的 Celery，50 人的规模不需要 RabbitMQ/Kafka
```

### 4.5 PostgreSQL + pgvector

| 配置项 | 值 | 理由 |
|---|---|---|
| 镜像 | `pgvector/pgvector:pg16` | pgvector 官方维护的镜像 |
| 存储 | 50Gi PersistentVolume | 向量维度 × 数量级 × 4 字节，10 万条 1536 维向量 = ~600MB，50Gi 足够用很久 |
| 副本 | 1 | 向量库目前只用于 RAG 检索，挂了自动重启即可 |
| 索引 | IVFFlat（初期）→ HNSW（后期向量过 10 万） | HNSW 构建慢但检索快，10 万以内 IVFFlat 够用 |

**为什么不用 Milvus / Chroma / Weaviate：**

| | pgvector | Milvus | Chroma |
|---|---|---|---|
| 部署复杂度 | ✅ 一个 PostgreSQL 实例 | ❌ 独立集群（etcd + 存储协调） | ⚠️ 又一个独立进程 |
| 运维成本 | ✅ 和 MySQL 类似，DBA 会管 | ❌ 专用运维知识 | ⚠️ 要单独备份/监控 |
| 50 人 10 万向量 | ✅ 毫秒级检索 | 大材小用 | ✅ 也能用 |
| 与业务数据 JOIN | ✅ 同一张表直接 JOIN | ❌ 要跨系统关联 | ❌ 要跨系统关联 |

**选 pgvector 的核心逻辑：** 你的 RAG 数据量在十万级，不需要分布式向量库；pgvector 和业务元数据在同一个数据库里，一条 SQL 同时查向量相似度 + 关联文档信息，不需要应用层拼接。而且少部署一个服务就少一份运维负担。

### 4.6 Ingress

```yaml
# k8s/ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: hify-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "50m"        # Agent 上传文件限制
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"     # SSE 长连接 5 分钟
    nginx.ingress.kubernetes.io/proxy-buffering: "off"        # SSE 必须关缓冲
spec:
  tls:
  - hosts:
    - hify.internal
    secretName: hify-tls
  rules:
  - host: hify.internal
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: hify-backend-svc
            port:
              number: 8080
      - path: /
        pathType: Prefix
        backend:
          service:
            name: hify-frontend-svc
            port:
              number: 80
```

**关键注解说明：**

| 注解 | 为什么需要 |
|---|---|
| `proxy-read-timeout: 300` | SSE 流式对话可能持续 2-3 分钟，Nginx 默认 60s 超时会切断连接 |
| `proxy-buffering: off` | SSE 需要逐块传输，缓冲打开的话 Nginx 会攒够 4KB 才发给浏览器——用户看 LLM 输出像卡住了一样 |
| `proxy-body-size: 50m` | 知识库上传 PDF/文档时会用 multipart |

---

## 五、K8s 资源规划

两台 8C16G 物理机 / 云虚拟机的小集群：

```
node-1 (8C 16G):
┌────────────────────────────────────────┐
│ hify-frontend     │ 100m CPU / 128Mi   │
│ hify-backend-1    │ 500m CPU / 512Mi   │
│ MySQL             │ 2000m CPU / 4Gi    │
│ Redis             │ 200m CPU / 256Mi   │
│ Ingress           │ 200m CPU / 256Mi   │
├────────────────────────────────────────┤
│ 已用:  3000m CPU / 5.1Gi              │
│ 剩余:  5000m CPU / 10.9Gi             │
└────────────────────────────────────────┘

node-2 (8C 16G):
┌────────────────────────────────────────┐
│ hify-backend-2    │ 500m CPU / 512Mi   │
│ PostgreSQL+pgvec  │ 1000m CPU / 2Gi    │
├────────────────────────────────────────┤
│ 已用:  1500m CPU / 2.5Gi              │
│ 剩余:  6500m CPU / 13.5Gi             │
└────────────────────────────────────────┘
```

**有余量的原因：** LLM API 调用慢但本身不消耗集群 CPU（在等网络 IO），这里预留的是 Agent 循环中工具执行、Embedding 计算、PDF 解析等本地计算的 CPU 用量。

---

## 六、配置文件管理

```
K8s 配置层级:
  ConfigMap     → 非敏感配置（数据库 URL、Redis 地址）
  Secret        → 敏感配置（数据库密码、LLM API Key、加密密钥）
  EnvFrom       → Pod 从 ConfigMap + Secret 注入环境变量
  Spring Boot   → application.yml 用 ${ENV_VAR:default} 读环境变量
```

```yaml
# k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: hify-config
data:
  SPRING_PROFILES_ACTIVE: "prod"
  MYSQL_HOST: "mysql-svc"
  MYSQL_PORT: "3306"
  MYSQL_DATABASE: "hify"
  REDIS_HOST: "redis-svc"
  REDIS_PORT: "6379"
  PGVECTOR_HOST: "pgvector-svc"
  PGVECTOR_PORT: "5432"
  PGVECTOR_DATABASE: "hify_vectors"

---
# k8s/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: hify-secret
type: Opaque
stringData:                     # stringData 不需要手动 base64 编码
  MYSQL_USER: "hify"
  MYSQL_PASSWORD: "<实际密码>"
  REDIS_PASSWORD: "<实际密码>"
  PGVECTOR_USER: "hify"
  PGVECTOR_PASSWORD: "<实际密码>"
  HIFY_ENCRYPTION_KEY: "<AES256 密钥>"    # 加密数据库中的 API Key
  OPENAI_API_KEY: "sk-xxx"                 # 放这里还是放数据库 Agent 配置？看策略
```

---

## 七、数据库初始化

```yaml
# K8s Job — 首次部署时跑 Flyway/Liquibase 迁移
apiVersion: batch/v1
kind: Job
metadata:
  name: hify-db-migrate
spec:
  ttlSecondsAfterFinished: 60
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: migrate
        image: hify-backend:latest
        command: ["java", "-jar", "/app/hify-app.jar", "--spring.flyway.migrate=true"]
        envFrom:
        - configMapRef:
            name: hify-config
        - secretRef:
            name: hify-secret
```

**pgvector 初始化 SQL（建向量表 + 索引）：**

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_vectors (
    id          BIGSERIAL PRIMARY KEY,
    knowledge_id BIGINT NOT NULL,
    chunk_index  INT NOT NULL,
    content      TEXT,
    embedding    vector(1536),      -- OpenAI text-embedding-ada-002 是 1536 维
    metadata     JSONB,
    created_at   TIMESTAMP DEFAULT NOW()
);

-- 初期用 IVFFlat（数据量 < 10 万）
CREATE INDEX ON document_vectors
  USING ivfflat (embedding vector_cosine_ops)
  WITH (lists = 100);

-- 后期数据量上来了换 HNSW
-- CREATE INDEX ON document_vectors
--   USING hnsw (embedding vector_cosine_ops)
--   WITH (m = 16, ef_construction = 200);
```

---

## 八、部署顺序

```
第 1 步: 创建 Namespace + ConfigMap + Secret
  kubectl apply -f k8s/namespace.yaml
  kubectl apply -f k8s/configmap.yaml
  kubectl apply -f k8s/secret.yaml

第 2 步: 起有状态服务（数据库）
  kubectl apply -f k8s/mysql-pvc.yaml
  kubectl apply -f k8s/mysql-deployment.yaml
  kubectl apply -f k8s/redis-deployment.yaml
  kubectl apply -f k8s/pgvector-pvc.yaml
  kubectl apply -f k8s/pgvector-deployment.yaml

第 3 步: 等数据库就绪 + 跑迁移
  kubectl wait --for=condition=ready pod -l app=mysql --timeout=120s
  kubectl apply -f k8s/db-migrate-job.yaml
  kubectl wait --for=condition=complete job/hify-db-migrate --timeout=60s

第 4 步: 起应用
  kubectl apply -f k8s/backend-deployment.yaml
  kubectl apply -f k8s/frontend-deployment.yaml
  kubectl apply -f k8s/ingress.yaml

第 5 步: 验证
  curl https://hify.internal/actuator/health
```

---

## 九、后续扩展方向（现在不做）

| 场景 | 扩展方式 | 触发条件 |
|---|---|---|
| 后端扛不住 | HPA 加 Pod（`maxReplicas: 4`），K8s 自动扩 | CPU 超过 70% 持续 5 分钟 |
| MySQL 瓶颈 | 加只读从库，读操作分流 | 慢查询日志里出现大量全表扫描 |
| Redis 瓶颈 | Redis Cluster 或用 K8s Operator 管理 | 内存使用超过 80% |
| 向量检索变慢 | 换 HNSW 索引 + 调整参数 | pgvector 单次查询 > 500ms |
| 用户上千 | 后端拆服务，先拆 conversation 模块独立部署 | 单体代码改不动，部署频率冲突 |
| 需要监控告警 | Prometheus + Grafana（Helm Chart 一键装） | 故障排查靠猜，需要可视化 |

---

> **核心原则：50 人场景下，稳定性靠 K8s 自动重启 + 2 副本够用，不需要搞多集群、服务网格、分布式追踪。先上线，让真实负载告诉你哪里该扩。**
