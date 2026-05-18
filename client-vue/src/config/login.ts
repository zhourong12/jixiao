/** 解析 .env 布尔开关：默认 true；false / 0 / off / no 为关闭 */
export function parseEnvFlag(value: string | undefined, defaultValue = true): boolean {
  if (value === undefined || value === "") return defaultValue;
  const v = value.trim().toLowerCase();
  return v !== "false" && v !== "0" && v !== "off" && v !== "no";
}

/** 构建时 VITE_PASSWORD_LOGIN_ENABLED，默认开启账密登录 */
export const passwordLoginEnabledFromEnv = parseEnvFlag(import.meta.env.VITE_PASSWORD_LOGIN_ENABLED, true);
