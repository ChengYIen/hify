#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Hify 一键启动脚本
# 功能：环境检查 → 安装依赖 → 构建后端 → 后台启动 →
#       等待健康检查通过 → 启动前端
# 任何一步失败立即终止并提示。
# 支持 Windows (Git Bash) 和 Linux。
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ---------- 可配置项 ----------
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"  # 默认对齐 application-dev.yml（root/123456 → hify_dev）
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5433}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
HEALTH_URL="http://localhost:${BACKEND_PORT}/api/v1/health"
HEALTH_MAX_RETRIES=30
HEALTH_SLEEP=2
FRONTEND_DIR="hify-web"
PID_DIR=".pids"
GRACE_PERIOD="${GRACE_PERIOD:-10}"

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
die()       { log_error "$*"; exit 1; }

# ---------- 操作系统检测 ----------
IS_WINDOWS=false
case "$(uname -s 2>/dev/null || echo 'Windows')" in
  CYGWIN*|MINGW*|MSYS*) IS_WINDOWS=true ;;
  *)                    IS_WINDOWS=false ;;
esac

# 跨平台命令封装
kill_port() {
  local port="$1"
  local pid
  pid=$(netstat -ano 2>/dev/null | grep -E ":${port}\s" | grep LISTENING | awk '{print $NF}' | head -1 || true)
  if [ -n "$pid" ]; then
    log_warn "端口 ${port} 已被占用 (PID=${pid})，正在终止..."
    if $IS_WINDOWS; then
      cmd //c "taskkill /PID ${pid} /F" 2>/dev/null || true
    else
      kill -9 "$pid" 2>/dev/null || true
    fi
    sleep 2
  fi
}

# ---------- 初始化 ----------
mkdir -p "$PID_DIR"
rm -f "$PID_DIR"/*.pid

# ---------- 0. 检查 Node.js ----------
log_info "===== 环境检查 ====="

if ! command -v node &>/dev/null; then
  die "未找到 Node.js，请安装 Node.js 18+ (https://nodejs.org)"
fi
log_info "Node.js $(node -v)"

if ! command -v java &>/dev/null; then
  die "未找到 JDK，请安装 JDK 17+"
fi
log_info "Java $(java -version 2>&1 | head -1)"

# ---------- 1. 检查 MySQL ----------
log_info "检测 MySQL (${MYSQL_HOST}:${MYSQL_PORT}) ..."
if command -v mysql &>/dev/null; then
  if ! mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" &>/dev/null; then
    log_warn "MySQL 连接失败（账号: ${MYSQL_USER}），继续启动（无数据库模式）"
    log_warn "如需完整功能，请确认 MySQL 已启动且账号密码正确"
  else
    log_info "MySQL 检测通过"
  fi
else
  if ! timeout 3 bash -c "echo >/dev/tcp/${MYSQL_HOST}/${MYSQL_PORT}" 2>/dev/null; then
    log_warn "MySQL 端口 ${MYSQL_PORT} 不可达，继续启动（无数据库模式）"
  else
    log_info "MySQL 端口可通（未验证账号密码）"
  fi
fi

# ---------- 2. 检查 Redis ----------
log_info "检测 Redis (${REDIS_HOST}:${REDIS_PORT}) ..."
if command -v redis-cli &>/dev/null; then
  if ! redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping &>/dev/null; then
    log_warn "Redis 连接失败，继续启动（Redis 不可用时会降级）"
  else
    log_info "Redis 检测通过"
  fi
else
  if ! timeout 3 bash -c "echo >/dev/tcp/${REDIS_HOST}/${REDIS_PORT}" 2>/dev/null; then
    log_warn "Redis 端口 ${REDIS_PORT} 不可达，继续启动"
  else
    log_info "Redis 端口可通"
  fi
fi

# ---------- 3. 检查 PostgreSQL/pgvector ----------
log_info "检测 PostgreSQL (${PG_HOST}:${PG_PORT}) ..."
if command -v pg_isready &>/dev/null; then
  if ! pg_isready -h "$PG_HOST" -p "$PG_PORT" &>/dev/null; then
    log_warn "PostgreSQL 连接失败，继续启动（知识库功能不可用）"
  else
    log_info "PostgreSQL 端口检测通过（请确认 vector 扩展已安装）"
  fi
else
  if ! timeout 3 bash -c "echo >/dev/tcp/${PG_HOST}/${PG_PORT}" 2>/dev/null; then
    log_warn "PostgreSQL 端口 ${PG_PORT} 不可达，继续启动（知识库功能不可用）"
  else
    log_info "PostgreSQL 端口可通（未验证账号、数据库和 vector 扩展）"
  fi
fi

# ---------- 4. 安装前端依赖 ----------
log_info "===== 安装前端依赖 ====="
cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
  log_info "首次运行，执行 npm install ..."
  npm install
else
  log_info "node_modules 已存在，跳过 npm install"
fi
cd ..

# ---------- 5. 构建后端 ----------
log_info "===== 构建后端 ====="
log_info "Maven 全量构建（跳过测试）..."
./mvnw clean install -DskipTests -q 2>&1 | tail -10
log_info "后端构建完成"

# ---------- 6. 后台启动后端 ----------
log_info "===== 启动后端 ====="
kill_port "$BACKEND_PORT"

./mvnw -pl hify-app spring-boot:run -q &
BACKEND_PID=$!
log_info "后端进程启动中 (PID=${BACKEND_PID}) ..."

# ---------- 7. 轮询等待健康检查 ----------
log_info "===== 等待健康检查 ====="

health_ok=false
for i in $(seq 1 $HEALTH_MAX_RETRIES); do
  sleep "$HEALTH_SLEEP"
  if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
    health_ok=true
    log_info "健康检查通过 (第 ${i} 次)"
    break
  fi
  log_warn "等待后端就绪 ... (${i}/${HEALTH_MAX_RETRIES})"
done

if [ "$health_ok" = false ]; then
  die "后端启动超时，健康检查未通过。请查看上方日志排查"
fi

# 记录实际监听端口的 PID（JVM 进程，非 mvn wrapper）
JAVA_PID=$(netstat -ano 2>/dev/null | grep -E ":${BACKEND_PORT}\s" | awk '{print $NF}' | head -1 || true)
if [ -n "$JAVA_PID" ]; then
  echo "$JAVA_PID" > "$PID_DIR/backend.pid"
  log_info "后端实际 PID=${JAVA_PID} 已记录到 ${PID_DIR}/backend.pid"
else
  echo "$BACKEND_PID" > "$PID_DIR/backend.pid"
  log_warn "无法获取后端实际 PID，使用 Maven wrapper PID=${BACKEND_PID}"
fi

# ---------- 8. 启动前端 ----------
log_info "===== 启动前端 ====="
cd "$FRONTEND_DIR"
npm run dev &
FRONTEND_PID=$!
cd ..
echo "$FRONTEND_PID" > "${PID_DIR}/frontend.pid"
log_info "前端 PID=${FRONTEND_PID} 已记录到 ${PID_DIR}/frontend.pid"

# ---------- 9. 输出信息 ----------
log_info "===== Hify 启动完成 ====="
echo ""
echo "  后端:  http://localhost:${BACKEND_PORT}"
echo "  前端:  http://localhost:${FRONTEND_PORT}"
echo ""
echo "停止服务:  ./stop.sh"
echo ""

# ---------- 10. 清理回调 ----------
cleanup() {
  log_warn "正在停止服务 ..."
  bash stop.sh 2>/dev/null || true
  log_info "已停止"
}
trap cleanup EXIT INT TERM

wait
