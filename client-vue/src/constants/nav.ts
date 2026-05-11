import type { MenuPermissionKey } from "@/types/api.interface";

export type NavItem = {
  path: string;
  label: string;
  menuKey: MenuPermissionKey;
};

/** 与 client/src/components/Layout.tsx navItems 顺序一致 */
export const NAV_ITEMS: NavItem[] = [
  { path: "/todo", label: "待办", menuKey: "todo" },
  { path: "/", label: "工作台", menuKey: "home" },
  { path: "/my-performance", label: "我的绩效", menuKey: "my_performance" },
  { path: "/performances", label: "绩效列表", menuKey: "performance_list" },
  {
    path: "/admin/performance-calibration",
    label: "绩效校准",
    menuKey: "admin_performance_calibration",
  },
  { path: "/admin/templates", label: "模板管理", menuKey: "admin_templates" },
  { path: "/admin/notifications", label: "通知管理", menuKey: "admin_notifications" },
  { path: "/admin/employees", label: "员工管理", menuKey: "admin_employees" },
  { path: "/admin/roles", label: "角色管理", menuKey: "admin_roles" },
  { path: "/admin/permissions", label: "权限管理", menuKey: "admin_permissions" },
  { path: "/admin/statistics-months", label: "周期与评选", menuKey: "admin_statistics_months" },
  { path: "/admin/system-config", label: "系统配置", menuKey: "admin_system_config" },
];
