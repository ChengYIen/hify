#!/usr/bin/env bash
set -euo pipefail

# Hify 本地发布包启动脚本。
# 目录结构约定：
#   backend/hify-app.jar
#   frontend/dist/
#   application.yml
#   start.sh / stop.sh

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${HIFY_HOME:-$SCRIPT_DIR}"
JAR_FILE="${HIFY_JAR:-$APP_HOME/backend/hify-app.jar}"
CONFIG_FILE="${HIFY_CONFIG_FILE:-$APP_HOME/application.yml}"
PID_FILE="${HIFY_PID_FILE:-$APP_HOME/hify.pid}"
LOG_DIR="${HIFY_LOG_DIR:-$APP_HOME/logs}"
LOG_FILE="${HIFY_LOG_FILE:-$LOG_DIR/hify.log}"
SERVER_PORT="${SERVER_PORT:-8080}"
HEALTH_URL="${HIFY_HEALTH_URL:-http://127.0.0.1:${SERVER_PORT}/api/v1/health}"
HEALTH_MAX_RETRIES="${HIFY_HEALTH_MAX_RETRIES:-30}"
HEALTH_SLEEP="${HIFY_HEALTH_SLEEP:-2}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-default}"

log_info() {
  printf '[INFO] %s\n' "$*"
}

log_error() {
  printf '[ERROR] %s\n' "$*" >&2
}

die() {
  log_error "$*"
  exit 1
}

pid_alive() {
  kill -0 "$1" 2>/dev/null
}

if ! command -v java >/dev/null 2>&1; then
  die "未找到 Java，请安装 Java 17 或更高版本"
fi

if ! command -v curl >/dev/null 2>&1; then
  die "未找到 curl，无法执行健康检查"
fi

[ -f "$JAR_FILE" ] || die "未找到后端 Jar：$JAR_FILE"
[ -f "$CONFIG_FILE" ] || die "未找到配置文件：$CONFIG_FILE"

if [ -f "$PID_FILE" ]; then
  EXISTING_PID="$(cat "$PID_FILE")"
  if pid_alive "$EXISTING_PID"; then
    die "Hify 已在运行（PID=$EXISTING_PID）"
  fi
  rm -f "$PID_FILE"
fi

mkdir -p "$LOG_DIR"
cd "$APP_HOME"

export SERVER_PORT
export SPRING_PROFILES_ACTIVE

log_info "启动 Hify ..."
log_info "Jar: $JAR_FILE"
log_info "配置: $CONFIG_FILE"
log_info "日志: $LOG_FILE"

JAVA_OPTS_ARGS=()
if [ -n "${JAVA_OPTS:-}" ]; then
  read -r -a JAVA_OPTS_ARGS <<< "$JAVA_OPTS"
fi

nohup java "${JAVA_OPTS_ARGS[@]}" \
  -jar "$JAR_FILE" \
  "--spring.profiles.active=${SPRING_PROFILES_ACTIVE}" \
  "--spring.config.location=file:${CONFIG_FILE}" \
  >> "$LOG_FILE" 2>&1 &

HIFY_PID=$!
echo "$HIFY_PID" > "$PID_FILE"

log_info "进程已启动（PID=$HIFY_PID），等待健康检查 ..."
for attempt in $(seq 1 "$HEALTH_MAX_RETRIES"); do
  if ! pid_alive "$HIFY_PID"; then
    rm -f "$PID_FILE"
    die "Hify 进程已退出，请查看日志：$LOG_FILE"
  fi

  if curl --fail --silent --show-error "$HEALTH_URL" >/dev/null 2>&1; then
    log_info "健康检查通过：$HEALTH_URL"
    printf 'Hify 已启动，PID=%s\n' "$HIFY_PID"
    exit 0
  fi

  sleep "$HEALTH_SLEEP"
  log_info "等待健康检查（${attempt}/${HEALTH_MAX_RETRIES}）..."
done

log_error "健康检查超时：$HEALTH_URL"
"$APP_HOME/stop.sh" || true
exit 1
