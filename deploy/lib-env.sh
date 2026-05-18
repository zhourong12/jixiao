# 部署环境配置：由 start-api.sh / start-web.sh source
# DEPLOY_ENV=prod（默认）-> deploy/.env
# DEPLOY_ENV=test          -> deploy/.env-test
# 也可显式指定 ENV_FILE 覆盖

deploy_resolve_env_file() {
  local root="$1"
  if [ -n "${ENV_FILE:-}" ]; then
    printf '%s\n' "$ENV_FILE"
    return 0
  fi
  local deploy_env="${DEPLOY_ENV:-prod}"
  case "$deploy_env" in
    test)
      printf '%s\n' "$root/deploy/.env-test"
      ;;
    prod | production)
      printf '%s\n' "$root/deploy/.env"
      ;;
    *)
      echo "未知 DEPLOY_ENV: $deploy_env（支持 prod、test）" >&2
      return 1
      ;;
  esac
}

deploy_load_env_file() {
  local env_file="$1"
  if [ ! -f "$env_file" ]; then
    echo "未找到环境配置：$env_file" >&2
    echo "正式：cp deploy/.env.example deploy/.env" >&2
    echo "测试：cp deploy/.env-test.example deploy/.env-test" >&2
    return 1
  fi
  set -a
  # shellcheck disable=SC1090
  . "$env_file"
  set +a
  return 0
}
