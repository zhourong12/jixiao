#!/usr/bin/env bash
# 正式（默认）：./deploy/start-api.sh          -> deploy/.env
# 测试：      DEPLOY_ENV=test ./deploy/start-api.sh -> deploy/.env-test
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib-env.sh
. "$(dirname "$0")/lib-env.sh"

JAR="${JAR:-$ROOT/dist/server/server-0.0.1-SNAPSHOT.jar}"
ENV_FILE="$(deploy_resolve_env_file "$ROOT")"
DEPLOY_ENV="${DEPLOY_ENV:-prod}"
JAVA_OPTS="${JAVA_OPTS:--XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0}"
API_PORT="${API_PORT:-8081}"
LOG="${LOG:-$ROOT/deploy/runtime/api.log}"
PID_FILE="${PID_FILE:-$ROOT/deploy/runtime/api.pid}"

if [ ! -f "$JAR" ]; then
  echo "未找到 JAR：$JAR" >&2
  exit 1
fi

deploy_load_env_file "$ENV_FILE"
echo "部署环境：$DEPLOY_ENV，配置文件：$ENV_FILE"

if [ -z "${SESSION_JWT_SECRET:-}" ]; then
  echo "请先在 $ENV_FILE 配置 SESSION_JWT_SECRET" >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "未找到 java 命令，请先安装 Java 8 运行环境" >&2
  exit 1
fi

port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn | awk -v p=":${port}" '$4 ~ p {found=1} END {exit !found}'
    return
  fi
  if command -v netstat >/dev/null 2>&1; then
    netstat -ltn | grep -q ":${port} "
    return
  fi
  return 1
}

stop_api() {
  if [ -f "$PID_FILE" ]; then
    local pid
    pid="$(cat "$PID_FILE")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
    rm -f "$PID_FILE"
  fi
  if port_in_use "$API_PORT" && command -v fuser >/dev/null 2>&1; then
    fuser -k "${API_PORT}/tcp" 2>/dev/null || true
    sleep 1
  fi
}

if [ "${BACKGROUND:-1}" = "1" ]; then
  mkdir -p "$(dirname "$LOG")"
  stop_api
  nohup java $JAVA_OPTS -jar "$JAR" >>"$LOG" 2>&1 &
  echo $! >"$PID_FILE"
  echo "后端已重启，PID=$(cat "$PID_FILE")"
  echo "日志：$LOG"
  exit 0
fi

stop_api
exec java $JAVA_OPTS -jar "$JAR"
