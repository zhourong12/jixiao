import type { MenuPermissionKey } from "@/types/api.interface";

export type NavItem = {
  path: string;
  label: string;
  menuKey: MenuPermissionKey;
};

/** 与 client/src/components/Layout.tsx navItems 顺序一致 */
export const NAV_ITEMS: NavItem[] = [
  { path: "/todo", label: "待办", menuKey: "todo" },
  { path: "/my-performance", label: "我的绩效", menuKey: "my_performance" },
  { path: "/performances", label: "绩效列表", menuKey: "performance_list" },
  {
    path: "/admin/performance-calibration",
    label: "绩效校准",
    menuKey: "admin_performance_calibration",
  },
  { path: "/admin/templates", label: "模板管理", menuKey: "admin_templates" },
  { path: "/admin/assessment-rules", label: "考核规则", menuKey: "admin_assessment_rules" },
  { path: "/admin/scoring-schemes", label: "评分方案", menuKey: "admin_scoring_schemes" },
  { path: "/admin/employees", label: "员工管理", menuKey: "admin_employees" },
  { path: "/admin/departments", label: "部门管理", menuKey: "admin_departments" },
  { path: "/admin/roles", label: "角色管理", menuKey: "admin_roles" },
  { path: "/admin/permissions", label: "权限管理", menuKey: "admin_permissions" },
  { path: "/admin/statistics-months", label: "周期与评选", menuKey: "admin_statistics_months" },
  { path: "/admin/system-config", label: "系统配置", menuKey: "admin_performance_feishu_task" },
  { path: "/admin/api-tokens", label: "API Token", menuKey: "admin_api_tokens" },
];
