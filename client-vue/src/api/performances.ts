import type {
  ApproveGoalRequest,
  CalibrationReviewRequest,
  CreatePerformanceRequest,
  CreatePerformanceResponse,
  FinalReviewRequest,
  PerformanceListParams,
  PerformanceListResponse,
  PerformanceRecord,
  RejectPerformanceRequest,
  SaveDraftRequest,
  SelectTemplateRequest,
  SubmitReviewRequest,
  SubmitReviewResponse,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function listPerformances(params: PerformanceListParams & { focus?: string }): Promise<PerformanceListResponse> {
  const q = new URLSearchParams();
  q.set("page", String(params.page));
  q.set("pageSize", String(params.pageSize));
  if (params.focus) q.set("focus", params.focus);
  else if (params.status) q.set("status", params.status);
  if (params.period) q.set("period", params.period);
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

export async function selectTemplate(id: string, body: SelectTemplateRequest): Promise<SubmitReviewResponse> {
  return apiJson<SubmitReviewResponse>(`/api/performances/${encodeURIComponent(id)}/select-template`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function createPerformance(body: CreatePerformanceRequest): Promise<CreatePerformanceResponse> {
  return apiJson<CreatePerformanceResponse>("/api/performances", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
