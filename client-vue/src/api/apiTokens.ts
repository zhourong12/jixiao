import type { ApiTokenListResponse, CreateApiTokenRequest, CreateApiTokenResponse } from "@/types/api.interface";
import { apiJson } from "./http";

const BASE = "/api/admin/api-tokens";

export function listApiTokens(): Promise<ApiTokenListResponse> {
  return apiJson<ApiTokenListResponse>(BASE);
}

export function createApiToken(body: CreateApiTokenRequest): Promise<CreateApiTokenResponse> {
  return apiJson<CreateApiTokenResponse>(BASE, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function deleteApiToken(id: number): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`${BASE}/${id}`, {
    method: "DELETE",
  });
}
