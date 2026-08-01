#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Hify 停止脚本
# 通过 PID 文件定位进程，先 SIGTERM 优雅退出，
# 超时后 SIGKILL 强制终止。
# 如果 PID 文件不存在，fallback 到端口查找。
# 支持 Windows (Git Bash) 和 Linux。
# ============================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PID_DIR=".pids"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
GRACE_PERIOD="${GRACE_PERIOD:-10}"
CHECK_INTERVAL=1

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---------- 操作系统检测 ----------
IS_WINDOWS=false
case "$(uname -s 2>/dev/null || echo 'Windows')" in
  CYGWIN*|MINGW*|MSYS*) IS_WINDOWS=true ;;
  *)                    IS_WINDOWS=false ;;
esac

# ---------- 工具函数 ----------

# 检查 PID 是否存在
pid_alive() {
  local pid="$1"
  if $IS_WINDOWS; then
    cmd //c "tasklist /FI \"PID eq ${pid}\" 2>NUL | findstr ${pid}" >/dev/null 2>&1
  else
    kill -0 "$pid" 2>/dev/null
  fi
}

# 发送终止信号
send_term() {
  local pid="$1"
  if $IS_WINDOWS; then
    cmd //c "taskkill /PID ${pid}" 2>/dev/null || true
  else
    kill -TERM "$pid" 2>/dev/null || true
  fi
}

# 强制终止
send_kill() {
  local pid="$1"
  if $IS_WINDOWS; then
    cmd //c "taskkill /F /PID ${pid}" 2>/dev/null || true
  else
    kill -9 "$pid" 2>/dev/null || true
  fi
}

# 停止单个进程：先 term 再 kill
stop_pid() {
  local pid="$1"
  local label="$2"

  if ! pid_alive "$pid"; then
    log_warn "${label} (PID=${pid}) 已退出"
    return 0
  fi

  log_info "发送 SIGTERM → ${label} (PID=${pid}) ..."
  send_term "$pid"

  local waited=0
  while pid_alive "$pid" && [ "$waited" -lt "$GRACE_PERIOD" ]; do
    sleep "$CHECK_INTERVAL"
    waited=$((waited + CHECK_INTERVAL))
    log_warn "等待 ${label} 退出 ... (${waited}s/${GRACE_PERIOD}s)"
  done

  if pid_alive "$pid"; then
    log_warn "${label} 未响应 SIGTERM，发送 SIGKILL ..."
    send_kill "$pid"
    sleep 1

    if pid_alive "$pid"; then
      log_error "${label} (PID=${pid}) 强制终止失败"
      return 1
    fi
  fi

  log_info "${label} (PID=${pid}) 已停止"
  return 0
}

# 根据端口查找 PID
find_pid_by_port() {
  local port="$1"
  netstat -ano 2>/dev/null | grep -E ":${port}\s" | grep LISTENING | awk '{print $NF}' | head -1 || true
}

# ---------- 停止后端 ----------
echo ""
log_info "===== 停止后端 ====="

BACKEND_PID=""
if [ -f "${PID_DIR}/backend.pid" ]; then
  BACKEND_PID=$(cat "${PID_DIR}/backend.pid")
  log_info "从 PID 文件读取后端 PID=${BACKEND_PID}"
else
  BACKEND_PID=$(find_pid_by_port "$BACKEND_PORT" || true)
  if [ -n "$BACKEND_PID" ]; then
    log_info "通过端口 ${BACKEND_PORT} 发现后端 PID=${BACKEND_PID}"
  fi
fi

if [ -z "$BACKEND_PID" ]; then
  log_warn "未找到后端进程"
else
  stop_pid "$BACKEND_PID" "后端"
fi

# ---------- 停止前端 ----------
echo ""
log_info "===== 停止前端 ====="

FRONTEND_PID=""
if [ -f "${PID_DIR}/frontend.pid" ]; then
  FRONTEND_PID=$(cat "${PID_DIR}/frontend.pid")
  log_info "从 PID 文件读取前端 PID=${FRONTEND_PID}"
else
  FRONTEND_PID=$(find_pid_by_port "$FRONTEND_PORT" || true)
  if [ -n "$FRONTEND_PID" ]; then
    log_info "通过端口 ${FRONTEND_PORT} 发现前端 PID=${FRONTEND_PID}"
  fi
fi

if [ -z "$FRONTEND_PID" ]; then
  log_warn "未找到前端进程"
else
  stop_pid "$FRONTEND_PID" "前端"
fi

# ---------- 清理 ----------
rm -f "${PID_DIR}/backend.pid" "${PID_DIR}/frontend.pid"
echo ""
log_info "===== 所有服务已停止 ====="
echo ""
