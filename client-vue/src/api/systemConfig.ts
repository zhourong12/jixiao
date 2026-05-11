import { apiJson } from "./http";

export interface SystemConfigItem {
  key: string;
  value: string;
  label: string;
  description: string;
  type: "number" | "string";
}

export async function getSystemConfig(): Promise<{ items: SystemConfigItem[] }> {
  return apiJson<{ items: SystemConfigItem[] }>("/api/admin/system-config", { method: "GET" });
}

export async function updateSystemConfig(body: {
  configs: Array<{ key: string; value: string }>;
}): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>("/api/admin/system-config", {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}
