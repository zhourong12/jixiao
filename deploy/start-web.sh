#!/usr/bin/env bash
# 正式（默认）：./deploy/start-web.sh          -> deploy/.env
# 测试：      DEPLOY_ENV=test ./deploy/start-web.sh -> deploy/.env-test
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib-env.sh
. "$(dirname "$0")/lib-env.sh"

ENV_FILE="$(deploy_resolve_env_file "$ROOT")"
DEPLOY_ENV="${DEPLOY_ENV:-prod}"
deploy_load_env_file "$ENV_FILE"
echo "部署环境：$DEPLOY_ENV，配置文件：$ENV_FILE"

CLIENT_DIR="${CLIENT_DIR:-$ROOT/dist/client}"
WEB_PORT="${WEB_PORT:-5174}"
API_UPSTREAM="${API_UPSTREAM:-127.0.0.1:8081}"
SERVER_NAME="${SERVER_NAME:-_}"
RUNTIME_DIR="${RUNTIME_DIR:-$ROOT/deploy/runtime}"
NGINX_CONF="$RUNTIME_DIR/nginx.conf"
PID_FILE="$RUNTIME_DIR/nginx.pid"
SITE_NAME="${SITE_NAME:-jixiao2}"
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
    echo "未找到 ${CLIENT_DIR}/index.html，请先执行 npm run package" >&2
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
if [ -z "$USE_SYSTEM_NGINX" ] && [ "$WEB_PORT" = "80" ] && port_in_use 80; then
  USE_SYSTEM_NGINX=1
fi

echo "前端目录：$CLIENT_DIR"
echo "监听端口：$WEB_PORT"
echo "API 上游：$API_UPSTREAM"

prepare_web_permissions

if [ "$USE_SYSTEM_NGINX" = "1" ]; then
  echo "检测到 ${WEB_PORT} 端口已被占用，改为写入系统 Nginx 站点配置"
  if [ "$(id -u)" -ne 0 ]; then
    write_server_block | sudo tee "$SITE_FILE" >/dev/null
    sudo ln -sf "$SITE_FILE" "$ENABLED_LINK"
    sudo nginx -t
    sudo systemctl reload nginx
  else
    write_server_block >"$SITE_FILE"
    ln -sf "$SITE_FILE" "$ENABLED_LINK"
    nginx -t
    systemctl reload nginx
  fi
  echo "站点配置：$SITE_FILE"
  echo "系统 Nginx 已重载"
  echo "访问地址：http://<服务器IP>:${WEB_PORT}/"
  echo "本机自检：curl -I http://127.0.0.1:${WEB_PORT}/"
  exit 0
fi

stop_web

mkdir -p "$RUNTIME_DIR"
CLIENT_DIR_ESCAPED="$(printf '%s' "$CLIENT_DIR" | sed 's/\\/\\\\/g; s/"/\\"/g')"
NGINX_USER="$(nginx_worker_user)"

cat > "$NGINX_CONF" <<EOF
user $NGINX_USER;
worker_processes 1;
error_log $RUNTIME_DIR/error.log warn;
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
  echo "访问地址：http://<服务器IP>:${WEB_PORT}/"
  echo "本机自检：curl -I http://127.0.0.1:${WEB_PORT}/"
  echo "错误日志：$RUNTIME_DIR/error.log"
  exit 0
fi

exec nginx -c "$NGINX_CONF" -g "daemon off;"
