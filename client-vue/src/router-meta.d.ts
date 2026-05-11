import "vue-router";

declare module "vue-router" {
  interface RouteMeta {
    title?: string;
    bare?: boolean;
    menuKey?: import("@/types/api.interface").MenuPermissionKey;
  }
}
