import type {
  BatchUpdateEmployeeAssessmentRuleRequest,
  BatchUpdateEmployeeAssessmentRuleResponse,
  CreateEmployeeRequest,
  EmployeeDirectoryListItem,
  EmployeeListResponse,
  EmployeeRoleOption,
  DepartmentOption,
  DepartmentTreeSubject,
  FeishuSubjectOption,
  FeishuUserProfileResponse,
  FeishuUserOptionsResponse,
  SyncEmployeesResponse,
  SyncFeishuEmployeesResponse,
  UpdateEmployeeHierarchyRequest,
  UpdateEmployeeRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

export function subjectCodeQueryValue(code: unknown): string | undefined {
  if (code == null) return undefined;
  const t = String(code).trim();
  return t || undefined;
}

export async function getAllEmployees(params?: { subjectCode?: string }): Promise<{
  items: EmployeeDirectoryListItem[];
}> {
  const q = new URLSearchParams();
  const sc = subjectCodeQueryValue(params?.subjectCode);
  if (sc) q.set("subjectCode", sc);
  const suffix = q.toString() ? `?${q.toString()}` : "";
  return apiJson<{ items: EmployeeDirectoryListItem[] }>(`/api/employees/all${suffix}`, {
    method: "GET",
  });
}

export async function getEmployees(params: {
  page?: number;
  pageSize?: number;
  keyword?: string;
  subjectCode?: string;
  departmentId?: string;
}): Promise<EmployeeListResponse> {
  const q = new URLSearchParams();
  if (params.page != null) q.set("page", String(params.page));
  if (params.pageSize != null) q.set("pageSize", String(params.pageSize));
  if (params.keyword) q.set("keyword", params.keyword);
  const sc = subjectCodeQueryValue(params.subjectCode);
  if (sc) q.set("subjectCode", sc);
  if (params.departmentId) q.set("departmentId", params.departmentId);
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

export async function batchUpdateEmployeeAssessmentRule(
  body: BatchUpdateEmployeeAssessmentRuleRequest,
): Promise<BatchUpdateEmployeeAssessmentRuleResponse> {
  return apiJson<BatchUpdateEmployeeAssessmentRuleResponse>("/api/employees/batch/assessment-rule", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deleteEmployee(employeeId: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/employees/${encodeURIComponent(employeeId)}`, { method: "DELETE" });
}

export async function getDepartmentTree(): Promise<{ items: DepartmentTreeSubject[] }> {
  return apiJson<{ items: DepartmentTreeSubject[] }>("/api/employees/department-tree", {
    method: "GET",
  });
}

export async function getDepartmentOptions(params?: {
  subjectCode?: string;
}): Promise<{ items: DepartmentOption[] }> {
  const q = new URLSearchParams();
  const sc = subjectCodeQueryValue(params?.subjectCode);
  if (sc) q.set("subjectCode", sc);
  const qs = q.toString();
  return apiJson<{ items: DepartmentOption[] }>(
    qs ? `/api/employees/department-options?${qs}` : "/api/employees/department-options",
    { method: "GET" },
  );
}

export async function getFeishuLoginSubjects(): Promise<{
  items: FeishuSubjectOption[];
  passwordLoginEnabled?: boolean;
}> {
  return apiJson<{ items: FeishuSubjectOption[]; passwordLoginEnabled?: boolean }>("/auth/feishu/subjects", {
    method: "GET",
  });
}

export async function getFeishuUserOptions(params: {
  subjectCode: string;
}): Promise<FeishuUserOptionsResponse> {
  const q = new URLSearchParams();
  q.set("subjectCode", params.subjectCode);
  return apiJson<FeishuUserOptionsResponse>(`/api/employees/feishu-user-options?${q.toString()}`, {
    method: "GET",
  });
}

export async function getFeishuUserProfile(params: {
  subjectCode: string;
  openId: string;
}): Promise<FeishuUserProfileResponse> {
  const q = new URLSearchParams();
  q.set("subjectCode", params.subjectCode);
  q.set("openId", params.openId);
  return apiJson<FeishuUserProfileResponse>(`/api/employees/feishu-user-profile?${q.toString()}`, {
    method: "GET",
  });
}

export async function syncEmployeesFromFeishu(
  subjectCodes?: string[],
): Promise<SyncFeishuEmployeesResponse> {
  try {
    const body =
      subjectCodes && subjectCodes.length > 0 ? { subjectCodes } : {};
    const data = await apiJson<SyncFeishuEmployeesResponse>("/api/employees/sync-from-feishu", {
      method: "POST",
      body: JSON.stringify(body),
    });
    if (!data || typeof data.success !== "boolean") {
      return {
        success: false,
        createdCount: 0,
        updatedCount: 0,
        failedCount: 0,
        subjects: [],
        message: "同步返回无效数据，请稍后重试",
      };
    }
    return data;
  } catch (error: unknown) {
    return {
      success: false,
      createdCount: 0,
      updatedCount: 0,
      failedCount: 0,
      subjects: [],
      message: error instanceof Error ? error.message : "同步失败，请检查网络或飞书配置",
    };
  }
}

export async function syncEmployeesFromLark(subjectCode: string, clearExisting: boolean): Promise<SyncEmployeesResponse> {
  try {
    const data = await apiJson<SyncEmployeesResponse>("/api/employees/sync-from-lark", {
      method: "POST",
      body: JSON.stringify({ clearExisting, subjectCode }),
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
