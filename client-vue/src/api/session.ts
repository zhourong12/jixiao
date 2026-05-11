import type { MenuPermissionKey } from "@/types/api.interface";
import { apiJson } from "./http";

export type SessionMeResponse = {
  authenticated?: boolean;
  user_id?: string;
  name?: string;
  role?: string;
  roles?: string[];
  menus?: Partial<Record<MenuPermissionKey, boolean>>;
};

export async function fetchSessionMe(): Promise<SessionMeResponse> {
  return apiJson<SessionMeResponse>("/api/session/me", { method: "GET" });
}

export async function postLogout(): Promise<void> {
  await apiJson<{ success?: boolean }>("/api/session/logout", { method: "POST" });
}
