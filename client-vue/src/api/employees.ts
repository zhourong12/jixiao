import type {
  CreateEmployeeRequest,
  EmployeeListResponse,
  EmployeeRoleOption,
  DepartmentOption,
  SyncEmployeesResponse,
  UpdateEmployeeHierarchyRequest,
  UpdateEmployeeRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function getEmployees(params: {
  page?: number;
  pageSize?: number;
  keyword?: string;
}): Promise<EmployeeListResponse> {
  const q = new URLSearchParams();
  if (params.page != null) q.set("page", String(params.page));
  if (params.pageSize != null) q.set("pageSize", String(params.pageSize));
  if (params.keyword) q.set("keyword", params.keyword);
  const suffix = q.toString() ? `?${q.toString()}` : "";
  return apiJson<EmployeeListResponse>(`/api/employees${suffix}`, { method: "GET" });
}

export async function getEmployeeRoleOptions(): Promise<{ items: EmployeeRoleOption[] }> {
  return apiJson<{ items: EmployeeRoleOption[] }>("/api/employees/role-options", { method: "GET" });
}

export async function updateEmployeeHierarchy(
  employeeId: string,
  body: UpdateEmployeeHierarchyRequest,
): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/employees/${encodeURIComponent(employeeId)}/hierarchy`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function createEmployee(body: CreateEmployeeRequest): Promise<{ id: string }> {
  return apiJson<{ id: string }>("/api/employees", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateEmployee(employeeId: string, body: UpdateEmployeeRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/employees/${encodeURIComponent(employeeId)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function deleteEmployee(employeeId: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/employees/${encodeURIComponent(employeeId)}`, { method: "DELETE" });
}

export async function getDepartmentOptions(): Promise<{ items: DepartmentOption[] }> {
  return apiJson<{ items: DepartmentOption[] }>("/api/employees/department-options", { method: "GET" });
}

export async function syncEmployeesFromLark(clearExisting: boolean): Promise<SyncEmployeesResponse> {
  try {
    const data = await apiJson<SyncEmployeesResponse>("/api/employees/sync-from-lark", {
      method: "POST",
      body: JSON.stringify({ clearExisting }),
    });
    if (!data || typeof data.success !== "boolean") {
      return { success: false, syncedCount: 0, message: "同步返回无效数据，请稍后重试" };
    }
    return data;
  } catch (error: unknown) {
    return {
      success: false,
      syncedCount: 0,
      message: error instanceof Error ? error.message : "同步失败，请检查网络或飞书配置",
    };
  }
}
