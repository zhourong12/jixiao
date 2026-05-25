#!/usr/bin/env bash
# 正式环境前端（单文件，无额外 source）
#   cp deploy/.env.example deploy/.env
#   ./deploy/start-web.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# ---------- 正式环境参数 ----------
WEB_PORT=80
WEB_DOMAIN="kpi.ccka.com"
API_UPSTREAM="127.0.0.1:8081"
RUNTIME_DIR="$ROOT/deploy/runtime"
LOG_DIR="$RUNTIME_DIR/logs"
LOG_RETENTION_DAYS=15
SERVER_NAME="$WEB_DOMAIN"
SITE_NAME="jixiao2"
ENV_FILE="$ROOT/deploy/.env"
CLIENT_DIR="${CLIENT_DIR:-$ROOT/dist/client}"

daily_log_file() {
  mkdir -p "$LOG_DIR"
  printf '%s/nginx-error-%s.log\n' "$LOG_DIR" "$(date +%F)"
}

prune_old_logs() {
  [ -d "$LOG_DIR" ] || return 0
  find "$LOG_DIR" -maxdepth 1 -type f -name 'nginx-error-*.log' -mtime +"${LOG_RETENTION_DAYS}" -delete 2>/dev/null || true
}

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

WEB_PORT=80
WEB_DOMAIN="kpi.ccka.com"
SERVER_NAME="$WEB_DOMAIN"
API_UPSTREAM="127.0.0.1:8081"

NGINX_ERROR_LOG="$(daily_log_file)"
prune_old_logs
NGINX_CONF="$RUNTIME_DIR/nginx.conf"
PID_FILE="$RUNTIME_DIR/nginx.pid"
SITE_FILE="${SITE_FILE:-/etc/nginx/sites-available/${SITE_NAME}.conf}"
ENABLED_LINK="${ENABLED_LINK:-/etc/nginx/sites-enabled/${SITE_NAME}.conf}"

if [ ! -d "$CLIENT_DIR" ]; then
  echo "未找到前端目录：$CLIENT_DIR" >&2
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "未安装 nginx，请先执行：sudo apt-get install -y nginx" >&2
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

prepare_web_permissions() {
  if [ ! -f "${CLIENT_DIR}/index.html" ]; then
    echo "未找到 ${CLIENT_DIR}/index.html，请先执行 npm run build:prod" >&2
    exit 1
  fi
  if [ "$(id -u)" -ne 0 ]; then
    return
  fi
  local dir="$CLIENT_DIR"
  while [ "$dir" != "/" ]; do
    chmod a+rx "$dir" 2>/dev/null || true
    dir="$(dirname "$dir")"
  done
  find "$CLIENT_DIR" -type d -exec chmod a+rx {} +
  find "$CLIENT_DIR" -type f -exec chmod a+r {} +
}

nginx_worker_user() {
  if [ "$(id -u)" -eq 0 ]; then
    printf '%s\n' "root"
    return
  fi
  printf '%s\n' "$(id -un)"
}

stop_web() {
  if [ -f "$NGINX_CONF" ] && [ -f "$PID_FILE" ]; then
    local pid
    pid="$(cat "$PID_FILE")"
    if kill -0 "$pid" 2>/dev/null; then
      nginx -c "$NGINX_CONF" -s quit 2>/dev/null || kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
  fi
  rm -f "$PID_FILE"
  if port_in_use "$WEB_PORT" && command -v fuser >/dev/null 2>&1; then
    fuser -k "${WEB_PORT}/tcp" 2>/dev/null || true
    sleep 1
  fi
}

write_server_block() {
  local client_dir_escaped
  client_dir_escaped="$(printf '%s' "$CLIENT_DIR" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  cat <<EOF
server {
  listen ${WEB_PORT};
  server_name ${SERVER_NAME};
  root "${client_dir_escaped}";
  index index.html;

  location /api/ {
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_pass http://${API_UPSTREAM};
  }

  location /auth/ {
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_pass http://${API_UPSTREAM};
  }

  location / {
    try_files \$uri \$uri/ /index.html;
  }
}
EOF
}

USE_SYSTEM_NGINX="${USE_SYSTEM_NGINX:-}"
# 域名走 80/443 时写入系统 Nginx 站点（与测试站 test-kpi.ccka.com 可并存）
if [ -z "$USE_SYSTEM_NGINX" ] && { [ "$WEB_PORT" = "80" ] || [ "$WEB_PORT" = "443" ]; }; then
  USE_SYSTEM_NGINX=1
fi
if [ -z "$USE_SYSTEM_NGINX" ] && [ "$WEB_PORT" = "80" ] && port_in_use 80; then
  USE_SYSTEM_NGINX=1
fi

echo "环境：正式"
echo "站点域名：$WEB_DOMAIN"
echo "配置：$ENV_FILE"
echo "前端目录：$CLIENT_DIR"
echo "监听端口：$WEB_PORT（server_name $SERVER_NAME）"
echo "API 上游：$API_UPSTREAM"
echo "日志目录：$LOG_DIR（保留 ${LOG_RETENTION_DAYS} 天）"
echo "Nginx 错误日志：$NGINX_ERROR_LOG"

prepare_web_permissions

if [ "$USE_SYSTEM_NGINX" = "1" ]; then
  echo "使用系统 Nginx（80 端口双域名请一并刷新正式+测试站点）"
  exec "$ROOT/deploy/reload-nginx-sites.sh"
fi

stop_web
mkdir -p "$RUNTIME_DIR" "$LOG_DIR"
CLIENT_DIR_ESCAPED="$(printf '%s' "$CLIENT_DIR" | sed 's/\\/\\\\/g; s/"/\\"/g')"
NGINX_USER="$(nginx_worker_user)"
NGINX_ERROR_ESCAPED="$(printf '%s' "$NGINX_ERROR_LOG" | sed 's/\\/\\\\/g; s/"/\\"/g')"

cat > "$NGINX_CONF" <<EOF
user $NGINX_USER;
worker_processes 1;
error_log "$NGINX_ERROR_ESCAPED" warn;
pid $PID_FILE;

events {
  worker_connections 1024;
}

http {
  include /etc/nginx/mime.types;
  default_type application/octet-stream;
  sendfile on;
  keepalive_timeout 65;

  server {
    listen $WEB_PORT;
    server_name $SERVER_NAME;
    root "$CLIENT_DIR_ESCAPED";
    index index.html;

    location /api/ {
      proxy_http_version 1.1;
      proxy_set_header Host \$host;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_pass http://$API_UPSTREAM;
    }

    location /auth/ {
      proxy_http_version 1.1;
      proxy_set_header Host \$host;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto \$scheme;
      proxy_pass http://$API_UPSTREAM;
    }

    location / {
      try_files \$uri \$uri/ /index.html;
    }
  }
}
EOF

if [ "${BACKGROUND:-1}" = "1" ]; then
  nginx -c "$NGINX_CONF"
  echo "独立 Nginx 已重启，PID 文件：$PID_FILE"
  if [ "$WEB_PORT" = "80" ] || [ "$WEB_PORT" = "443" ]; then
    echo "访问地址：http://${WEB_DOMAIN}/"
  else
    echo "访问地址：http://${WEB_DOMAIN}:${WEB_PORT}/"
  fi
  exit 0
fi

exec nginx -c "$NGINX_CONF" -g "daemon off;"
