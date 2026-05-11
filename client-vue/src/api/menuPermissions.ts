import type { MenuPermissionsMeResponse } from "@/types/api.interface";
import { apiJson } from "./http";

export async function getMenuPermissionsMe(): Promise<MenuPermissionsMeResponse> {
  return apiJson<MenuPermissionsMeResponse>("/api/menu-permissions/me", { method: "GET" });
}
