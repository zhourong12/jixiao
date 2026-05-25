import { apiJson } from "./http";

export interface FeishuJssdkConfig {
  appId: string;
  timestamp: number;
  nonceStr: string;
  signature: string;
}

export async function getFeishuJssdkConfig(url: string): Promise<FeishuJssdkConfig> {
  const q = new URLSearchParams({ url });
  return apiJson<FeishuJssdkConfig>(`/api/feishu/jssdk-config?${q.toString()}`, { method: "GET" });
}

export async function getFeishuAppBadgeEnabled(): Promise<{ enabled: boolean }> {
  return apiJson<{ enabled: boolean }>("/api/feishu/app-badge/enabled", { method: "GET" });
}

export async function syncFeishuAppBadge(): Promise<{
  success: boolean;
  badgeNum: number;
  skipped?: boolean;
}> {
  return apiJson<{ success: boolean; badgeNum: number }>("/api/feishu/app-badge/sync", {
    method: "POST",
  });
}

/** 上报角标 H5 调试日志到服务端 logs/feishu-badge-client.log */
export async function postFeishuBadgeClientLog(
  lines: string[],
): Promise<{ ok: boolean; written: number }> {
  return apiJson<{ ok: boolean; written: number }>("/api/feishu/badge-client-log", {
    method: "POST",
    body: JSON.stringify({ lines }),
  });
}
