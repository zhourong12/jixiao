import { defineStore } from "pinia";
import { getFeishuLoginSubjects } from "@/api/employees";

/** 与后端 system_config.password_login_enabled 一致：仅 1 / true 为开启 */
export function parsePasswordLoginEnabled(value: unknown): boolean {
  if (value === true || value === 1) return true;
  if (value === false || value === 0 || value === null || value === undefined) return false;
  const s = String(value).trim().toLowerCase();
  return s === "1" || s === "true";
}

export const useAuthLoginStore = defineStore("authLogin", {
  state: () => ({
    passwordLoginEnabled: false,
    loaded: false,
  }),
  actions: {
    async refresh() {
      try {
        const res = await getFeishuLoginSubjects();
        this.passwordLoginEnabled = parsePasswordLoginEnabled(res.passwordLoginEnabled);
      } catch {
        this.passwordLoginEnabled = false;
      } finally {
        this.loaded = true;
      }
    },
  },
});
