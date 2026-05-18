import type {
  ScoringScheme,
  ScoringSchemeListResponse,
  CreateScoringSchemeRequest,
  UpdateScoringSchemeRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

/** 后端列表可能为 `list` 或 `items`，统一为 list */
type ScoringSchemeListRaw = { total: number; list?: ScoringScheme[]; items?: ScoringScheme[] };

export async function listScoringSchemes(
  page = 1,
  pageSize = 200,
): Promise<ScoringSchemeListResponse> {
  const q = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  const raw = await apiJson<ScoringSchemeListRaw>(`/api/admin/scoring-schemes?${q}`, {
    method: "GET",
  });
  const list = raw.list ?? raw.items ?? [];
  const total = typeof raw.total === "number" ? raw.total : 0;
  return { total, list };
}

export async function getScoringScheme(id: string): Promise<ScoringScheme> {
  return apiJson<ScoringScheme>(`/api/admin/scoring-schemes/${encodeURIComponent(id)}`, { method: "GET" });
}

export async function createScoringScheme(body: CreateScoringSchemeRequest): Promise<{ id: string }> {
  return apiJson<{ id: string }>("/api/admin/scoring-schemes", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateScoringScheme(id: string, body: UpdateScoringSchemeRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/scoring-schemes/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function deleteScoringScheme(id: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/scoring-schemes/${encodeURIComponent(id)}`, { method: "DELETE" });
}
