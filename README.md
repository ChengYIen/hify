# Hify — AI Agent 对话平台

> Spring Boot 3 + Vue 3 全栈项目，1 人开发，50 人内部使用。

---

## 拉代码到跑起来（5 分钟）

### 1. 前置环境

| 软件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | `java -version` 确认 |
| Node.js | 18+ | `node -v` 确认 |
| MySQL | 8.0+ | 业务数据存储 |
| Redis | 7+ | 缓存 / 会话状态 |
| PostgreSQL | 16+ + pgvector | 知识库向量存储 |

> **JDK 25+ 用户注意：** 先执行 `make fix-jdk`（或手动创建 `.mvn/jvm.config` 添加 `--add-opens`）

### 2. 拉代码

```bash
git clone <repo-url> hify
cd hify
```

### 3. 初始化数据库

```bash
# 创建开发数据库（仅创建数据库，表结构由 Flyway 负责）
mysql -uroot -p < db/init.sql

# 修改连接参数（如果需要）
# 通过 MYSQL_*/PG_*/REDIS_* 环境变量覆盖默认值
```

### 4. 启动

```bash
# 方式一：make（推荐）
make start

# 方式二：直接运行脚本
bash start.sh
```

脚本会自动：
1. 检查 MySQL / Redis / PostgreSQL 端口是否可用
2. `npm install`（首次）
3. Maven 全量构建（跳过测试）
4. 后台启动后端，轮询等待健康检查通过
5. 启动前端 Vite 开发服务器

### 5. 访问

| 服务 | 地址 |
|---|---|
| 前端页面 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| 健康检查 | http://localhost:8080/api/v1/health |

### 6. 停止

```bash
make stop   # 或 bash stop.sh
```

---

## 部署

正式部署只连接外部 MySQL、Redis 和 PostgreSQL + pgvector，不在 Hify
交付包中启动这些基础设施。

- Docker Compose：复制 `deploy/env.template` 为 `.env` 后执行
  `docker compose up -d --build`
- Kubernetes：`kubectl apply -k k8s`
- JAR/systemd：参考 [docs/deployment-guide.md](docs/deployment-guide.md)

Docker Compose 使用根目录 `.env` 注入外部 MySQL、Redis、
PostgreSQL/pgvector 的连接信息；外部服务地址不能填写 `localhost`。
宿主机端口冲突时，修改 `.env` 中的 `FRONTEND_EXPOSE_PORT` 或
`BACKEND_EXPOSE_PORT`。

```bash
cp deploy/env.template .env
vi .env
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

完整的外部服务配置、数据库初始化、文件持久化和探针说明见
[docs/deployment-guide.md](docs/deployment-guide.md)。

## 常用命令

```bash
make start      # 一键启动
make stop       # 优雅停止
make restart    # 重启
make build      # 构建前后端
make clean      # 清理构建产物
make package    # 打包 tar.gz
make help       # 显示所有命令
```

---

## 项目结构

```
hify/
├── hify-app/                 # Spring Boot 启动入口
├── hify-common/              # 全局基础设施（配置、工具类、异常）
├── hify-shared/              # 模块间共享契约（接口 + DTO，无实现）
├── hify-module-provider/     # 模型提供商管理
├── hify-module-agent/        # Agent 配置
├── hify-module-conversation/ # 对话引擎
├── hify-module-knowledge/    # 知识库 RAG
├── hify-module-workflow/     # 工作流执行
├── hify-module-mcp/          # MCP 工具接入
├── hify-web/                 # Vue 3 前端
├── deploy/local/             # 本地发布包模板与启停脚本
├── db/init.sql               # 数据库初始化脚本
├── start.sh / stop.sh        # 启停脚本
├── Makefile                  # 构建/启停/打包
└── docs/                     # 详细设计文档
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端框架 | Spring Boot 3.3 + MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 + PostgreSQL 16 (pgvector) |
| 缓存 | Redis 7 + Lettuce |
| 韧性 | Resilience4j 熔断 + Spring Retry |
| 前端 | Vue 3 + Vite + Element Plus + Pinia |
| 构建 | Maven (mvnw) + npm |

## FAQ

**Q: 启动时报 "无法获取后端实际 PID"？**
A: 这只是一个警告，不影响运行。后端可能通过 `mvnw` 包装器启动，PID 已用 Maven wrapper 的 PID。

**Q: MySQL 连接失败？**
A: 检查 `MYSQL_*` 环境变量是否与本地 MySQL 一致。默认用户名为 `root`，密码为 `123456`。

**Q: 前端代理报 502？**
A: 确保后端 8080 端口已启动成功（访问 http://localhost:8080/api/v1/health 确认）。

**Q: Windows 上没有 `make`？**
A: `choco install make` 安装，或直接用 `bash start.sh`。
