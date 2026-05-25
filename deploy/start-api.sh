#!/usr/bin/env bash
# 正式环境后端（单文件，无额外 source）
#   cp deploy/.env.example deploy/.env
#   ./deploy/start-api.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ---------- 正式环境参数 ----------
APP_ORIGIN="https://kpi.ccka.com"
API_PORT=8081
RUNTIME_DIR="$ROOT/deploy/runtime"
LOG_DIR="$RUNTIME_DIR/logs"
PID_FILE="$RUNTIME_DIR/api.pid"
LOG_RETENTION_DAYS=15
JIXIAO2_JDBC_URL="jdbc:mysql://127.0.0.1:3306/jixiao?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
ENV_FILE="$ROOT/deploy/.env"
JAR="${JAR:-$ROOT/dist/server/server-0.0.1-SNAPSHOT.jar}"
JAVA_OPTS="${JAVA_OPTS:--XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0}"

daily_log_file() {
  mkdir -p "$LOG_DIR"
  printf '%s/api-%s.log\n' "$LOG_DIR" "$(date +%F)"
}

prune_old_logs() {
  [ -d "$LOG_DIR" ] || return 0
  find "$LOG_DIR" -maxdepth 1 -type f -name 'api-*.log' -mtime +"${LOG_RETENTION_DAYS}" -delete 2>/dev/null || true
}

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

# 正式环境：端口 / 域名相关项由脚本固定，无需在 .env 重复配置
API_PORT=8081
export API_PORT SERVER_PORT="$API_PORT" JIXIAO2_JDBC_URL
export JIXIAO2_PUBLIC_APP_URL="$APP_ORIGIN"
export FEISHU_REDIRECT_URI="${APP_ORIGIN}/feishu-callback"
export JIXIAO2_CORS_ALLOWED_ORIGIN_PATTERNS="https://kpi.ccka.com,http://kpi.ccka.com,http://localhost:*,http://127.0.0.1:*"

LOG="$(daily_log_file)"
prune_old_logs

if [ ! -f "$JAR" ]; then
  echo "未找到 JAR：$JAR，请先 npm run build:prod" >&2
  exit 1
fi

if [ -z "${SESSION_JWT_SECRET:-}" ]; then
  echo "请先在 $ENV_FILE 配置 SESSION_JWT_SECRET" >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "未找到 java 命令" >&2
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

echo "环境：正式"
echo "站点：$APP_ORIGIN"
echo "配置：$ENV_FILE（仅密钥/飞书 app 等）"
echo "数据库：$JIXIAO2_JDBC_URL"
echo "监听端口：$API_PORT"
echo "日志目录：$LOG_DIR（保留 ${LOG_RETENTION_DAYS} 天）"
echo "当日日志：$LOG"

if [ "${BACKGROUND:-1}" = "1" ]; then
  stop_api
  nohup java $JAVA_OPTS -jar "$JAR" >>"$LOG" 2>&1 &
  echo $! >"$PID_FILE"
  echo "后端已重启，PID=$(cat "$PID_FILE")"
  exit 0
fi

stop_api
exec java $JAVA_OPTS -Dserver.port="$API_PORT" -jar "$JAR"
