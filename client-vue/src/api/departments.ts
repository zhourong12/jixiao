import type { OrgDepartmentListItem } from "@/types/api.interface";
import { subjectCodeQueryValue } from "@/api/employees";
import { apiJson } from "./http";

export async function listOrgDepartments(params: {
  page?: number;
  pageSize?: number;
  subjectCode?: string;
  keyword?: string;
}): Promise<{ items: OrgDepartmentListItem[]; total: number; page: number; pageSize: number }> {
  const q = new URLSearchParams();
  if (params.page != null) q.set("page", String(params.page));
  if (params.pageSize != null) q.set("pageSize", String(params.pageSize));
  const sc = subjectCodeQueryValue(params.subjectCode);
  if (sc) q.set("subjectCode", sc);
  if (params.keyword?.trim()) q.set("keyword", params.keyword.trim());
  const suffix = q.toString() ? `?${q.toString()}` : "";
  return apiJson(`/api/admin/departments${suffix}`, { method: "GET" });
}

export async function syncOrgDepartmentsFromEmployees(): Promise<{ success: boolean; upserted: number }> {
  return apiJson("/api/admin/departments/sync-from-employees", { method: "POST" });
}

export async function createOrgDepartment(body: {
  subjectCode: string;
  name: string;
  sortOrder?: number;
}): Promise<{ success: boolean; id: string }> {
  return apiJson("/api/admin/departments", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateOrgDepartment(
  id: string,
  body: { name: string; sortOrder?: number },
): Promise<{ success: boolean }> {
  return apiJson(`/api/admin/departments/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function deleteOrgDepartment(id: string): Promise<{ success: boolean }> {
  return apiJson(`/api/admin/departments/${encodeURIComponent(id)}`, { method: "DELETE" });
}
