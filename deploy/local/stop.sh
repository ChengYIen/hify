#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${HIFY_HOME:-$SCRIPT_DIR}"
PID_FILE="${HIFY_PID_FILE:-$APP_HOME/hify.pid}"
STOP_TIMEOUT="${HIFY_STOP_TIMEOUT:-30}"
CHECK_INTERVAL="${HIFY_STOP_CHECK_INTERVAL:-1}"

log_info() {
  printf '[INFO] %s\n' "$*"
}

log_warn() {
  printf '[WARN] %s\n' "$*"
}

pid_alive() {
  kill -0 "$1" 2>/dev/null
}

# Git Bash 下 $! 记录的是 MSYS 伪 PID，kill 无法可靠映射到原生进程；
# 通过 ps -W 找到对应 Windows PID，再走 taskkill 兜底。
is_windows() {
  uname -s 2>/dev/null | grep -qiE 'mingw|msys|cygwin'
}

windows_winpid() {
  ps -W 2>/dev/null | awk -v msys_pid="$1" '$1 == msys_pid {print $4; exit}'
}

if [ ! -f "$PID_FILE" ]; then
  log_warn "未找到 PID 文件，Hify 可能未运行"
  exit 0
fi

HIFY_PID="$(cat "$PID_FILE")"
if ! pid_alive "$HIFY_PID"; then
  rm -f "$PID_FILE"
  log_info "Hify 已经退出（PID=$HIFY_PID）"
  exit 0
fi

log_info "发送 SIGTERM，等待 Hify 优雅退出（PID=$HIFY_PID）..."
kill -TERM "$HIFY_PID" 2>/dev/null || true

waited=0
while pid_alive "$HIFY_PID" && [ "$waited" -lt "$STOP_TIMEOUT" ]; do
  sleep "$CHECK_INTERVAL"
  waited=$((waited + CHECK_INTERVAL))
done

if pid_alive "$HIFY_PID"; then
  log_warn "等待 ${STOP_TIMEOUT}s 后进程仍未退出，发送 SIGKILL"
  kill -KILL "$HIFY_PID" 2>/dev/null || true
  if is_windows; then
    WINPID="$(windows_winpid "$HIFY_PID")"
    if [ -n "$WINPID" ]; then
      log_warn "Git Bash 无法直接终止原生进程，改用 taskkill（WINPID=$WINPID）"
      taskkill //F //PID "$WINPID" >/dev/null 2>&1 || true
      for _ in $(seq 1 10); do
        pid_alive "$HIFY_PID" || break
        sleep 1
      done
    fi
  fi
fi

rm -f "$PID_FILE"
if pid_alive "$HIFY_PID"; then
  if is_windows && [ -z "$(windows_winpid "$HIFY_PID")" ]; then
    # 原生进程已不存在，仅剩 MSYS 残留表项，视为已停止
    :
  else
    log_warn "无法确认 Hify 进程已退出（PID=$HIFY_PID）"
    exit 1
  fi
fi

log_info "Hify 已停止"