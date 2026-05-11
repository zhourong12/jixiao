import type { MenuPermissionKey } from "@/types/api.interface";
import { defineStore } from "pinia";
import { fetchSessionMe, postLogout, type SessionMeResponse } from "@/api/session";
import { getMenuPermissionsMe } from "@/api/menuPermissions";
import { GUEST_MENU_KEYS } from "@/constants/guestMenus";

export const useSessionStore = defineStore("session", {
  state: () => ({
    loaded: false,
    authenticated: false as boolean,
    userId: "" as string,
    name: "" as string,
    role: "employee" as string,
    roles: [] as string[],
    menus: null as Partial<Record<MenuPermissionKey, boolean>> | null,
    permLoading: true,
  }),
  getters: {
    loggedIn(state): boolean {
      return state.authenticated && !!state.userId;
    },
  },
  actions: {
    allow(key: MenuPermissionKey): boolean {
      if (!this.loggedIn) {
        return GUEST_MENU_KEYS.has(key);
      }
      if (!this.menus) {
        return !String(key).startsWith("admin_");
      }
      return this.menus[key] !== false;
    },

    async refreshSession() {
      this.loaded = false;
      try {
        const d: SessionMeResponse = await fetchSessionMe();
        if (d?.authenticated && d.user_id) {
          this.authenticated = true;
          this.userId = d.user_id;
          this.name = d.name || d.user_id;
          this.role = d.role ?? "employee";
          this.roles = d.roles ?? [];
          this.menus = d.menus ?? null;
        } else {
          this.authenticated = false;
          this.userId = "";
          this.name = "";
          this.role = "employee";
          this.roles = [];
          this.menus = null;
        }
      } catch {
        this.authenticated = false;
        this.userId = "";
        this.menus = null;
      } finally {
        this.loaded = true;
      }
    },

    async refreshMenuPermissions(forceApi = false) {
      this.permLoading = true;
      try {
        if (!this.loggedIn) {
          this.menus = null;
          this.role = "employee";
          return;
        }
        if (
          !forceApi &&
          this.authenticated &&
          this.loaded &&
          this.menus != null &&
          Object.keys(this.menus).length > 0
        ) {
          return;
        }
        const res = await getMenuPermissionsMe();
        this.menus = res.menus;
        this.role = res.role;
        this.roles = res.roles ?? [];
      } catch {
        this.menus = null;
      } finally {
        this.permLoading = false;
      }
    },

    async bootstrap() {
      await this.refreshSession();
      await this.refreshMenuPermissions(false);
    },

    async logout() {
      try {
        await postLogout();
      } catch {
        /* ignore */
      }
      this.authenticated = false;
      this.userId = "";
      this.name = "";
      this.menus = null;
      this.loaded = true;
      this.permLoading = false;
    },
  },
});
