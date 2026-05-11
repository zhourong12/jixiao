import { createRouter, createWebHistory } from "vue-router";
import { useSessionStore } from "@/stores/session";
import AppLayout from "@/layouts/AppLayout.vue";
import LoginView from "@/views/LoginView.vue";
import HomeView from "@/views/HomeView.vue";
import TodoView from "@/views/TodoView.vue";
import PerformanceListView from "@/views/PerformanceListView.vue";
import PerformanceDetailView from "@/views/PerformanceDetailView.vue";
import MyPerformanceView from "@/views/MyPerformanceView.vue";
import PerformanceCalibrationView from "@/views/admin/PerformanceCalibrationView.vue";
import TemplateManageView from "@/views/admin/TemplateManageView.vue";
import NotificationManageView from "@/views/admin/NotificationManageView.vue";
import EmployeeManageView from "@/views/admin/EmployeeManageView.vue";
import RoleManageView from "@/views/admin/RoleManageView.vue";
import PermissionManageView from "@/views/admin/PermissionManageView.vue";
import StatisticsMonthsView from "@/views/admin/StatisticsMonthsView.vue";
import SystemConfigView from "@/views/admin/SystemConfigView.vue";
import NotFoundView from "@/views/NotFoundView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { bare: true } },
    {
      path: "/",
      component: AppLayout,
      children: [
        { path: "", name: "home", component: HomeView, meta: { menuKey: "home" as const, title: "工作台" } },
        { path: "todo", name: "todo", component: TodoView, meta: { menuKey: "todo" as const, title: "待办" } },
        {
          path: "my-performance",
          name: "my-performance",
          component: MyPerformanceView,
          meta: { menuKey: "my_performance" as const, title: "我的绩效" },
        },
        {
          path: "performances",
          name: "performances",
          component: PerformanceListView,
          meta: { menuKey: "performance_list" as const, title: "绩效列表" },
        },
        {
          path: "performance-list",
          redirect: "/performances",
        },
        {
          path: "performances/:id",
          name: "performance-detail",
          component: PerformanceDetailView,
          meta: { menuKey: "performance_list" as const, title: "绩效详情" },
        },
        {
          path: "admin/performance-calibration",
          component: PerformanceCalibrationView,
          meta: { title: "绩效校准", menuKey: "admin_performance_calibration" as const },
        },
        {
          path: "admin/templates",
          component: TemplateManageView,
          meta: { title: "模板管理", menuKey: "admin_templates" as const },
        },
        {
          path: "admin/notifications",
          component: NotificationManageView,
          meta: { title: "通知管理", menuKey: "admin_notifications" as const },
        },
        {
          path: "admin/employees",
          component: EmployeeManageView,
          meta: { title: "员工管理", menuKey: "admin_employees" as const },
        },
        {
          path: "admin/roles",
          component: RoleManageView,
          meta: { title: "角色管理", menuKey: "admin_roles" as const },
        },
        {
          path: "admin/permissions",
          component: PermissionManageView,
          meta: { title: "权限管理", menuKey: "admin_permissions" as const },
        },
        {
          path: "admin/statistics-months",
          component: StatisticsMonthsView,
          meta: { title: "周期与评选", menuKey: "admin_statistics_months" as const },
        },
        {
          path: "admin/system-config",
          component: SystemConfigView,
          meta: { title: "系统配置", menuKey: "admin_system_config" as const },
        },
        {
          path: ":pathMatch(.*)*",
          name: "notfound",
          component: NotFoundView,
          meta: { title: "页面不存在" },
        },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  if (!session.loaded) {
    await session.bootstrap();
  }
  if (to.name === "login" && session.loggedIn) {
    return { path: "/", replace: true };
  }
  return true;
});

export default router;
