import { apiJson } from "./http";

export async function getAuthConfig(): Promise<{ passwordLoginEnabled: boolean }> {
  return apiJson<{ passwordLoginEnabled: boolean }>("/api/admin/auth-config", { method: "GET" });
}

export async function patchAuthConfig(body: {
  passwordLoginEnabled: boolean;
}): Promise<{ success: boolean; passwordLoginEnabled: boolean }> {
  return apiJson<{ success: boolean; passwordLoginEnabled: boolean }>("/api/admin/auth-config", {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}
