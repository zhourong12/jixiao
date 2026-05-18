import type {
  AssessmentRuleListItem,
  CreateAssessmentRuleRequest,
  UpdateAssessmentRuleRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function listAssessmentRules(
  page = 1,
  pageSize = 200,
): Promise<{ items: AssessmentRuleListItem[]; total: number }> {
  const q = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  return apiJson<{ items: AssessmentRuleListItem[]; total: number }>(`/api/admin/assessment-rules?${q}`, {
    method: "GET",
  });
}

export async function getAssessmentRule(id: string): Promise<AssessmentRuleListItem> {
  return apiJson<AssessmentRuleListItem>(`/api/admin/assessment-rules/${encodeURIComponent(id)}`, { method: "GET" });
}

export async function createAssessmentRule(body: CreateAssessmentRuleRequest): Promise<{ id: string }> {
  return apiJson<{ id: string }>("/api/admin/assessment-rules", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateAssessmentRule(id: string, body: UpdateAssessmentRuleRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/assessment-rules/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function deleteAssessmentRule(id: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/assessment-rules/${encodeURIComponent(id)}`, { method: "DELETE" });
}
