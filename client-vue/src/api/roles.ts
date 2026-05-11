import type {
  CreateRbacRoleRequest,
  MenuPermissionMatrixResponse,
  RbacRoleItem,
  UpdateMenuPermissionsBody,
  UpdateRbacRoleRequest,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function listRbacRoles(): Promise<{ items: RbacRoleItem[] }> {
  return apiJson<{ items: RbacRoleItem[] }>("/api/admin/menu-permissions/roles", { method: "GET" });
}

export async function createRbacRole(body: CreateRbacRoleRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>("/api/admin/menu-permissions/roles", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateRbacRole(roleKey: string, body: UpdateRbacRoleRequest): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/menu-permissions/roles/${encodeURIComponent(roleKey)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function deleteRbacRole(roleKey: string): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>(`/api/admin/menu-permissions/roles/${encodeURIComponent(roleKey)}`, {
    method: "DELETE",
  });
}

export async function getMenuPermissionMatrix(): Promise<MenuPermissionMatrixResponse> {
  return apiJson<MenuPermissionMatrixResponse>("/api/admin/menu-permissions/matrix", { method: "GET" });
}

export async function updateMenuPermissionsForRole(body: UpdateMenuPermissionsBody): Promise<{ success: boolean }> {
  return apiJson<{ success: boolean }>("/api/admin/menu-permissions", {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
