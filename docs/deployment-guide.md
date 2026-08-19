# Hify 部署交付说明

Hify 的部署包只包含 Hify 自身。MySQL、Redis、PostgreSQL + pgvector
均由客户提供，Hify 通过环境变量连接，不在 Docker Compose 或 Kubernetes
清单中启动这些基础设施。

## 运行时要求

- Java 17 或更高版本
- Docker 部署不需要目标机器安装 Java
- JAR 部署需要目标机器已有 Java 17+
- 外部 MySQL 8+
- 外部 Redis 7+
- 外部 PostgreSQL 16+，并启用 `vector` 扩展
- Hify 到外部 LLM/Embedding 服务的网络访问

前端构建需要 Node.js，但运行时只需要 Nginx 或 Hify 前端镜像。

## 配置项

Docker Compose 部署时复制 `deploy/env.template` 为根目录 `.env`，
替换所有示例值。`.env` 不提交到 Git。

| 配置 | 说明 |
|---|---|
| `MYSQL_HOST/PORT/DATABASE/USER/PASSWORD` | MySQL 业务库 |
| `PG_HOST/PORT/DATABASE/USER/PASSWORD` | PostgreSQL 向量库 |
| `REDIS_HOST/PORT/DATABASE/PASSWORD` | Redis |
| `HIFY_UPLOAD_DIR` | 知识库原始文件目录 |
| `FLYWAY_ENABLED` | 是否自动执行 MySQL 迁移 |
| `SERVER_PORT` | Spring Boot 监听端口 |
| `FRONTEND_EXPOSE_PORT` | 宿主机前端端口，默认 `8080` |
| `BACKEND_EXPOSE_PORT` | 宿主机后端端口，默认 `8081` |

Kubernetes 的 `hify-secret` 模板包含 `OPENAI_API_KEY`。当前 Hify 的 Provider
业务仍以管理页面写入 MySQL 的加密 API Key 为准；仅创建该环境变量不会自动
创建或覆盖数据库中的 OpenAI Provider 配置。

## 数据库初始化

### MySQL

新环境：

1. DBA 创建 `hify` 数据库和应用账号。
2. 设置 `FLYWAY_ENABLED=true`。
3. 启动 Hify，Flyway 自动执行 V1 到 V4。

已有手工建表环境：

1. 先检查现有表与 `hify-app/src/main/resources/db/migration` 的差异。
2. 将 `FLYWAY_BASELINE_ON_MIGRATE=true`、`FLYWAY_BASELINE_VERSION=3`。
3. 启动 Hify，让 Flyway 从 V4 开始补表。V4 是幂等的完整业务表基线。

不要在生产环境使用 `db/init.sql` 建表；该文件只负责本地开发数据库。

### PostgreSQL + pgvector

在外部 PostgreSQL 数据库上执行：

```text
db/postgresql/V1__create_vector_schema.sql
```

执行账号需要安装扩展的权限。Embedding 模型输出维度必须与
`document_chunk.embedding VECTOR(1536)` 一致。

## Docker Compose

Docker Compose 只启动：

- `backend`
- `frontend`

启动前准备 `.env`，并确保容器能够访问外部 MySQL、Redis、PostgreSQL
和 LLM 服务：

```text
cp deploy/env.template .env
vi .env
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

外部服务地址必须填写容器可访问的真实 IP、DNS 或 Docker 可路由地址，
不能填写 `localhost`。容器内的 `localhost` 指向当前容器自身，不是宿主机
或其他数据库容器。

`docker compose ps` 应确认 `backend` 和 `frontend` 都处于 `Running`
状态；后端启动问题可通过 `docker compose logs -f backend` 查看。

浏览器访问 `http://<host>:${FRONTEND_EXPOSE_PORT}`，默认是
`http://<host>:8080`。后端直接健康检查端口默认是
`http://<host>:${BACKEND_EXPOSE_PORT}/api/v1/health`，默认端口为 `8081`。
宿主机端口被占用时，只修改 `.env` 中的
`FRONTEND_EXPOSE_PORT` 或 `BACKEND_EXPOSE_PORT`。

知识库原始文件存放在 Compose volume `hify_upload` 中，不能删除该 volume。

## Kubernetes

Kubernetes 清单默认使用 `hify` namespace、2 个后端副本和一个上传文件 PVC。
前端默认使用 2 个副本，并通过 `NodePort 30080` 对外提供访问。

部署前需要修改：

- `k8s/configmap.yaml` 中的外部服务地址
- `k8s/secret.yaml.example`，创建实际的 `hify-secret`，填入数据库密码、
  Redis 密码、pgvector 密码和 OpenAI API Key
- `k8s/kustomization.yaml` 中的镜像仓库和版本
- `k8s/ingress.yaml` 中的域名和 TLS Secret

建议先执行：

```text
kubectl apply -f k8s/namespace.yaml
kubectl -n hify create secret generic hify-secret \
  --from-literal=MYSQL_USER=hify \
  --from-literal=MYSQL_PASSWORD=<value> \
  --from-literal=PG_USER=hify \
  --from-literal=PG_PASSWORD=<value> \
  --from-literal=REDIS_PASSWORD=<value> \
  --from-literal=OPENAI_API_KEY=<value> \
  --from-literal=JWT_SECRET=<value>
kubectl apply -k k8s
```

清单中的后端 liveness、readiness 和 startup probe 都使用：

```text
GET /api/v1/health
```

该接口只表示 Hify 进程已能接收 HTTP 请求，不检查外部数据库和 Redis。

## 文件持久化边界

- JAR/systemd：`HIFY_UPLOAD_DIR` 必须指向持久化磁盘。
- Docker：使用 `hify_upload` volume 或绑定宿主机目录。
- Kubernetes：使用 `hify-upload` PVC。
- 2 个后端副本共享上传目录时，需要底层 PVC 支持多节点读写；跨节点部署
  前应改为 RWX 共享存储或对象存储。

## 多副本限制

Provider 定时健康检查和文档异步处理都运行在应用进程内。继续水平扩容
前需要增加分布式锁、任务队列或独立 Worker，并确保上传目录使用共享存储。

## 本地部署 tar 包

执行：

```text
make package
```

产物默认位于 `target/hify-<version>.tar.gz`，解压后包含：

- `backend/hify-app.jar`
- `frontend/dist/`
- `application.yml`
- `start.sh`
- `stop.sh`

该包只要求目标机器已有 Java 17+ 和 curl，不包含 JDK、Node.js、
MySQL、Redis 或 PostgreSQL。MySQL、Redis、PostgreSQL + pgvector
必须提前准备好，并通过环境变量或 `application.yml` 注入连接信息。
`start.sh` 只启动后端 Jar；`frontend/dist` 由现有 Nginx 或其他静态
文件服务器托管，并将 `/api/` 反向代理到 Hify 后端端口。

启动：

```text
export MYSQL_HOST=mysql.example.internal
export MYSQL_PORT=3306
export MYSQL_DATABASE=hify
export MYSQL_USER=hify
export MYSQL_PASSWORD='change-me'
export PG_HOST=postgres.example.internal
export PG_PORT=5432
export PG_DATABASE=hify
export PG_USER=hify
export PG_PASSWORD='change-me'
export REDIS_HOST=redis.example.internal
export REDIS_PORT=6379
export REDIS_DATABASE=0
export REDIS_PASSWORD='change-me'

./start.sh
curl http://127.0.0.1:8080/api/v1/health
./stop.sh
```

也可以直接编辑解压目录中的 `application.yml`。脚本会在启动时
等待 `/api/v1/health` 返回 HTTP 200，停止时先发送 SIGTERM 并等待
进程退出，超过 `HIFY_STOP_TIMEOUT` 才会强制终止。
