import { apiJson } from "./http";
import type { PerformanceFeishuTaskNodeConfigItem } from "@/types/api.interface";

export interface SystemConfigResponse {
  appBadgeEnabled: boolean;
  feishuTaskEnabled: boolean;
  feishuTaskItems: PerformanceFeishuTaskNodeConfigItem[];
  passwordLoginEnabled: boolean;
}

export async function getSystemConfig(): Promise<SystemConfigResponse> {
  return apiJson<SystemConfigResponse>("/api/admin/platform-settings", { method: "GET" });
}

export async function patchSystemConfig(body: {
  appBadgeEnabled?: boolean;
  feishuTaskEnabled?: boolean;
  feishuTaskItems?: Array<{ nodeKey: string; dueDays: number }>;
  passwordLoginEnabled?: boolean;
}): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>("/api/admin/platform-settings", {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}
