import type {
  ApproveGoalRequest,
  BatchIssueSelfReviewResponse,
  CalibrationReviewRequest,
  CreatePerformanceRequest,
  CreatePerformanceResponse,
  FinalReviewRequest,
  PerformanceExportItem,
  PerformanceFilterParams,
  PerformanceListParams,
  PerformanceListResponse,
  PerformanceRecord,
  RejectPerformanceRequest,
  AdminRejectSelfReviewRequest,
  SaveDraftRequest,
  SaveGoalIndicatorsRequest,
  SelectTemplateRequest,
  SubmitReviewRequest,
  SubmitReviewResponse,
} from "@/types/api.interface";
import type { PerformanceStatus } from "@/types/api.interface";
import { apiJson } from "./http";

function appendStatusQuery(q: URLSearchParams, status?: PerformanceStatus | PerformanceStatus[]) {
  if (!status) return;
  const list = (Array.isArray(status) ? status : [status]).map((s) => String(s).trim()).filter(Boolean);
  if (list.length) q.set("status", list.join(","));
}

export async function listPerformances(params: PerformanceListParams & { focus?: string }): Promise<PerformanceListResponse> {
  const q = new URLSearchParams();
  q.set("page", String(params.page));
  q.set("pageSize", String(params.pageSize));
  if (params.focus) q.set("focus", params.focus);
  else appendStatusQuery(q, params.status);
  if (params.period) q.set("period", params.period);
  if (params.subjectCode) q.set("subjectCode", params.subjectCode);
  if (params.departmentId) q.set("departmentId", params.departmentId);
  if (params.employeeName) q.set("employeeName", params.employeeName);
  return apiJson<PerformanceListResponse>(`/api/performances?${q.toString()}`, { method: "GET" });
}

export async function getPerformanceDetail(id: string): Promise<PerformanceRecord> {
  return apiJson<PerformanceRecord>(`/api/performances/${encodeURIComponent(id)}`, { method: "GET" });
}

export async function getSupervisorCalibrationQueue(params: PerformanceListParams): Promise<PerformanceListResponse> {
  const q = new URLSearchParams();
  if (params.period) q.set("period", params.period);
  if (params.subjectCode) q.set("subjectCode", params.subjectCode);
  if (params.departmentId) q.set("departmentId", params.departmentId);
  if (params.employeeName) q.set("employeeName", params.employeeName);
  q.set("page", String(params.page));
  q.set("pageSize", String(params.pageSize));
  return apiJson<PerformanceListResponse>(`/api/performances/calibration/supervisor-queue?${q.toString()}`, {
    method: "GET",
  });
}

export async function savePerformanceDraft(id: string, body: SaveDraftRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/performances/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function submitPerformanceReview(id: string, body: SubmitReviewRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/submit`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function rejectPerformance(id: string, body: RejectPerformanceRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/performances/${encodeURIComponent(id)}/reject`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function adminRejectSelfReview(
  body: AdminRejectSelfReviewRequest,
): Promise<{ success: boolean; newStatus?: string }> {
  return apiJson<{ success: boolean; newStatus?: string }>(`/api/performances/ops/reject-self-review`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function approveGoal(id: string, body: ApproveGoalRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/approve-goal`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function finalReview(id: string, body: FinalReviewRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/final-review`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function calibratePerformance(id: string, body: CalibrationReviewRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/calibrate`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function confirmPerformanceResult(id: string): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/confirm-result`, {
    method: "POST",
  });
}

export async function selectTemplate(id: string, body: SelectTemplateRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/select-template`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function saveGoalIndicators(id: string, body: SaveGoalIndicatorsRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/goal-indicators`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function getDefaultNodeDeadlines(
  period: string,
): Promise<{ nodeDeadlines: Record<string, string> }> {
  const q = new URLSearchParams({ period });
  return apiJson<{ nodeDeadlines: Record<string, string> }>(
    `/api/performances/node-deadlines-default?${q.toString()}`,
    { method: "GET" },
  );
}

export async function startSelfReview(id: string): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/start-self-review`, {
    method: "POST",
  });
}

export async function issueSelfReview(id: string): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/issue-self-review`, {
    method: "POST",
  });
}

export async function batchIssueSelfReview(recordIds: string[]): Promise<BatchIssueSelfReviewResponse> {
  return apiJson<BatchIssueSelfReviewResponse>("/api/performances/batch-issue-self-review", {
    method: "POST",
    body: JSON.stringify({ recordIds }),
  });
}

export async function rollbackPlanToDeadlineAnchor(
  id: string,
  body?: { deadline?: string },
): Promise<SubmitReviewResponse & { deadlineFlowAnchor?: string; deadline?: string }> {
  return apiJson<SubmitReviewResponse & { deadlineFlowAnchor?: string; deadline?: string }>(
    `/api/performances/${encodeURIComponent(id)}/rollback-plan-anchor`,
    { method: "POST", body: JSON.stringify(body ?? {}) },
  );
}

export async function createPerformance(body: CreatePerformanceRequest): Promise<CreatePerformanceResponse> {
  return apiJson<CreatePerformanceResponse>("/api/performances", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function exportPerformances(
  params: PerformanceFilterParams & { focus?: string },
): Promise<{ items: PerformanceExportItem[] }> {
  const q = new URLSearchParams();
  if (params.focus) q.set("focus", params.focus);
  else appendStatusQuery(q, params.status);
  if (params.period) q.set("period", params.period);
  if (params.subjectCode) q.set("subjectCode", params.subjectCode);
  if (params.departmentId) q.set("departmentId", params.departmentId);
  if (params.employeeName) q.set("employeeName", params.employeeName);
  return apiJson<{ items: PerformanceExportItem[] }>(`/api/performances/export?${q.toString()}`, { method: "GET" });
}

export async function getPerformanceMonthPeriods(): Promise<{ items: Array<{ periodKey: string; name: string }> }> {
  return apiJson<{ items: Array<{ periodKey: string; name: string }> }>("/api/performances/create/month-periods", {
    method: "GET",
  });
}

export async function getPerformanceFilterMonthPeriods(): Promise<{ items: Array<{ periodKey: string; name: string }> }> {
  return apiJson<{ items: Array<{ periodKey: string; name: string }> }>("/api/performances/filter/month-periods", {
    method: "GET",
  });
}

export async function deletePerformance(id: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/performances/${encodeURIComponent(id)}`, { method: "DELETE" });
}
