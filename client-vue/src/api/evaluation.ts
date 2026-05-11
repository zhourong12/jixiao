import type {
  AwardTypeItem,
  CreateEvaluationPeriodRequest,
  CreatePeriodAwardRequest,
  EvaluationPeriodItem,
  EvaluationPeriodType,
  PerformanceLeaderboardResponse,
  PeriodAwardItem,
  UpdateEvaluationPeriodRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function getEvaluationPeriods(
  periodType?: EvaluationPeriodType,
): Promise<{ items: EvaluationPeriodItem[] }> {
  const q = periodType ? `?period_type=${encodeURIComponent(periodType)}` : "";
  return apiJson<{ items: EvaluationPeriodItem[] }>(`/api/admin/evaluation-periods${q}`, { method: "GET" });
}

export async function createEvaluationPeriod(body: CreateEvaluationPeriodRequest): Promise<EvaluationPeriodItem> {
  return apiJson<EvaluationPeriodItem>("/api/admin/evaluation-periods", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateEvaluationPeriod(
  id: string,
  body: UpdateEvaluationPeriodRequest,
): Promise<EvaluationPeriodItem> {
  return apiJson<EvaluationPeriodItem>(`/api/admin/evaluation-periods/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteEvaluationPeriod(id: string): Promise<void> {
  await apiJson(`/api/admin/evaluation-periods/${encodeURIComponent(id)}`, { method: "DELETE" });
}

export async function getAwardTypes(): Promise<{ items: AwardTypeItem[] }> {
  return apiJson<{ items: AwardTypeItem[] }>("/api/admin/award-types", { method: "GET" });
}

export async function getPerformancePeriods(): Promise<{ items: string[] }> {
  return apiJson<{ items: string[] }>("/api/admin/evaluation/performance-periods", { method: "GET" });
}

export async function getEvaluationLeaderboard(params: {
  scope: "month" | "quarter";
  key: string;
  departmentIds?: string[];
}): Promise<PerformanceLeaderboardResponse> {
  const search = new URLSearchParams();
  search.set("scope", params.scope);
  search.set("key", params.key);
  if (params.departmentIds?.length) {
    search.set("departmentIds", params.departmentIds.join(","));
  }
  return apiJson<PerformanceLeaderboardResponse>(`/api/admin/evaluation/leaderboard?${search.toString()}`, {
    method: "GET",
  });
}

export async function getPeriodAwards(periodId: string): Promise<{ items: PeriodAwardItem[] }> {
  const q = new URLSearchParams({ periodId });
  return apiJson<{ items: PeriodAwardItem[] }>(`/api/admin/evaluation/awards?${q.toString()}`, { method: "GET" });
}

export async function createPeriodAward(body: CreatePeriodAwardRequest): Promise<PeriodAwardItem> {
  return apiJson<PeriodAwardItem>("/api/admin/evaluation/awards", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deletePeriodAward(id: string): Promise<void> {
  await apiJson(`/api/admin/evaluation/awards/${encodeURIComponent(id)}`, { method: "DELETE" });
}
