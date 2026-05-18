import type {
  CreateTemplateRequest,
  PerformanceTemplate,
  TemplateListItem,
  UpdateTemplateRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function listTemplates(page: number, pageSize: number, type?: string): Promise<{ items: TemplateListItem[]; total: number }> {
  const q = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  if (type) q.set("type", type);
  return apiJson<{ items: TemplateListItem[]; total: number }>(`/api/admin/templates?${q.toString()}`, {
    method: "GET",
  });
}

export async function getTemplates(): Promise<TemplateListItem[]> {
  const res = await listTemplates(1, 500);
  return res.items || [];
}

export async function getTemplateDetail(id: string): Promise<PerformanceTemplate> {
  return apiJson<PerformanceTemplate>(`/api/admin/templates/${encodeURIComponent(id)}`, { method: "GET" });
}

export async function createTemplate(body: CreateTemplateRequest): Promise<{ id: string }> {
  return apiJson<{ id: string }>("/api/admin/templates", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateTemplate(id: string, body: UpdateTemplateRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/templates/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function toggleTemplateStatus(id: string): Promise<{ success: boolean; newStatus: string }> {
  return apiJson<{ success: boolean; newStatus: string }>(
    `/api/admin/templates/${encodeURIComponent(id)}/toggle-status`,
    { method: "POST" },
  );
}

export async function copyTemplate(id: string): Promise<{ newTemplateId: string }> {
  return apiJson<{ newTemplateId: string }>(`/api/admin/templates/${encodeURIComponent(id)}/copy`, {
    method: "POST",
  });
}

export async function deleteTemplate(id: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/templates/${encodeURIComponent(id)}`, { method: "DELETE" });
}
