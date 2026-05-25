#!/usr/bin/env bash
# 正式 + 测试双域名共用系统 Nginx（80 端口），一次写入两个 server 块并 reload。
#   ./deploy/reload-nginx-sites.sh
# 生成副本（便于查看）：deploy/nginx/sites/*.conf
# 系统路径：/etc/nginx/sites-available/jixiao2.conf、jixiao2-test.conf
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLIENT_DIR="${CLIENT_DIR:-$ROOT/dist/client}"
SITES_DIR="$ROOT/deploy/nginx/sites"
PROD_DOMAIN="kpi.ccka.com"
TEST_DOMAIN="test-kpi.ccka.com"
PROD_API="127.0.0.1:8081"
TEST_API="127.0.0.1:8082"
PROD_SITE_NAME="jixiao2"
TEST_SITE_NAME="jixiao2-test"

if [ ! -d "$CLIENT_DIR" ]; then
  echo "未找到前端目录：$CLIENT_DIR，请先 npm run build:prod" >&2
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  echo "未安装 nginx" >&2
  exit 1
fi

client_dir_escaped="$(printf '%s' "$CLIENT_DIR" | sed 's/\\/\\\\/g; s/"/\\"/g')"

write_site_conf() {
  local outfile="$1"
  local server_name="$2"
  local api_upstream="$3"
  cat >"$outfile" <<EOF
server {
  listen 80;
  server_name ${server_name};
  root "${client_dir_escaped}";
  index index.html;

  location /api/ {
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_pass http://${api_upstream};
  }

  location /auth/ {
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_pass http://${api_upstream};
  }

  location / {
    try_files \$uri \$uri/ /index.html;
  }
}
EOF
}

stop_standalone_nginx() {
  local dir pid conf
  for dir in "$ROOT/deploy/runtime" "$ROOT/deploy/runtime-test"; do
    pid="$dir/nginx.pid"
    conf="$dir/nginx.conf"
    if [ -f "$conf" ] && [ -f "$pid" ]; then
      local p
      p="$(cat "$pid")"
      if kill -0 "$p" 2>/dev/null; then
        nginx -c "$conf" -s quit 2>/dev/null || kill "$p" 2>/dev/null || true
        sleep 1
      fi
    fi
    rm -f "$pid"
  done
}

install_to_system() {
  local name="$1"
  local src="$2"
  local site_file="/etc/nginx/sites-available/${name}.conf"
  local enabled_link="/etc/nginx/sites-enabled/${name}.conf"
  if [ "$(id -u)" -ne 0 ]; then
    sudo cp "$src" "$site_file"
    sudo ln -sf "$site_file" "$enabled_link"
  else
    cp "$src" "$site_file"
    ln -sf "$site_file" "$enabled_link"
  fi
}

mkdir -p "$SITES_DIR"
stop_standalone_nginx

PROD_SRC="$SITES_DIR/${PROD_DOMAIN}.conf"
TEST_SRC="$SITES_DIR/${TEST_DOMAIN}.conf"
write_site_conf "$PROD_SRC" "$PROD_DOMAIN" "$PROD_API"
write_site_conf "$TEST_SRC" "$TEST_DOMAIN" "$TEST_API"

install_to_system "$PROD_SITE_NAME" "$PROD_SRC"
install_to_system "$TEST_SITE_NAME" "$TEST_SRC"

if [ "$(id -u)" -ne 0 ]; then
  sudo nginx -t
  sudo systemctl reload nginx
else
  nginx -t
  systemctl reload nginx
fi

echo "已安装系统 Nginx 站点："
echo "  正式  http://${PROD_DOMAIN}/  -> ${PROD_API}"
echo "  测试  http://${TEST_DOMAIN}/  -> ${TEST_API}"
echo "仓库内副本："
echo "  ${PROD_SRC}"
echo "  ${TEST_SRC}"
echo "系统路径："
echo "  /etc/nginx/sites-available/${PROD_SITE_NAME}.conf"
echo "  /etc/nginx/sites-available/${TEST_SITE_NAME}.conf"
