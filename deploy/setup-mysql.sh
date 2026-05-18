#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
SQL_FILE="${SQL_FILE:-$ROOT/server-java/database/sql/initial.sql}"
DB_NAME="${DB_NAME:-jixiao}"
DB_USER="${DB_USER:-root}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

if [ -n "${JIXIAO2_JDBC_PASSWORD:-}" ]; then
  DB_PASSWORD="$JIXIAO2_JDBC_PASSWORD"
else
  DB_PASSWORD="${DB_PASSWORD:-}"
fi

if [ ! -f "$SQL_FILE" ]; then
  echo "未找到初始化 SQL：$SQL_FILE" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y mysql-server
  systemctl enable --now mysql
fi

mysql_exec() {
  if [ -n "$DB_PASSWORD" ]; then
    MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$@"
    return
  fi
  mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$@"
}

mysql_exec -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql_exec "$DB_NAME" <"$SQL_FILE"

echo "MySQL 已就绪：${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "请在 ${ENV_FILE} 配置 JIXIAO2_JDBC_URL / JIXIAO2_JDBC_USER / JIXIAO2_JDBC_PASSWORD"
