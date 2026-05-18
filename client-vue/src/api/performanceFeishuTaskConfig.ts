import { apiJson } from "./http";
import type { PerformanceFeishuTaskNodeConfigItem } from "@/types/api.interface";

export async function getPerformanceFeishuTaskConfig(): Promise<{
  enabled: boolean;
  items: PerformanceFeishuTaskNodeConfigItem[];
}> {
  return apiJson<{ enabled: boolean; items: PerformanceFeishuTaskNodeConfigItem[] }>(
    "/api/admin/performance-feishu-task-config",
    { method: "GET" },
  );
}

export async function patchPerformanceFeishuTaskConfig(body: {
  enabled?: boolean;
  items?: Array<{ nodeKey: string; dueDays: number }>;
}): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>("/api/admin/performance-feishu-task-config", {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}
