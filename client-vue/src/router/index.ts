import { createRouter, createWebHistory } from "vue-router";
import { useAuthLoginStore } from "@/stores/authLogin";
import { useSessionStore } from "@/stores/session";
import AppLayout from "@/layouts/AppLayout.vue";
import LoginView from "@/views/LoginView.vue";
import FeishuCallbackView from "@/views/FeishuCallbackView.vue";
import FeishuLoginEntryView from "@/views/FeishuLoginEntryView.vue";
import TodoView from "@/views/TodoView.vue";
import PerformanceListView from "@/views/PerformanceListView.vue";
import PerformanceDetailView from "@/views/PerformanceDetailView.vue";
import MyPerformanceView from "@/views/MyPerformanceView.vue";
import PerformanceCalibrationView from "@/views/admin/PerformanceCalibrationView.vue";
import TemplateManageView from "@/views/admin/TemplateManageView.vue";
import AssessmentRuleManageView from "@/views/admin/AssessmentRuleManageView.vue";
import ScoringSchemeManageView from "@/views/admin/ScoringSchemeManageView.vue";
import EmployeeManageView from "@/views/admin/EmployeeManageView.vue";
import DepartmentManageView from "@/views/admin/DepartmentManageView.vue";
import RoleManageView from "@/views/admin/RoleManageView.vue";
import PermissionManageView from "@/views/admin/PermissionManageView.vue";
import StatisticsMonthsView from "@/views/admin/StatisticsMonthsView.vue";
import SystemConfigView from "@/views/admin/SystemConfigView.vue";
import ApiTokenManageView from "@/views/admin/ApiTokenManageView.vue";
import NotFoundView from "@/views/NotFoundView.vue";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/login", name: "login", component: LoginView, meta: { bare: true } },
    {
      path: "/feishu-callback",
      name: "feishu-callback",
      component: FeishuCallbackView,
      meta: { bare: true, title: "飞书登录" },
    },
    {
      path: "/feishu/login/:subjectCode?",
      name: "feishu-login-entry",
      component: FeishuLoginEntryView,
      meta: { bare: true, title: "飞书登录" },
    },
    {
      path: "/",
      component: AppLayout,
      children: [
        { path: "", redirect: { name: "todo" } },
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
          path: "admin/assessment-rules",
          component: AssessmentRuleManageView,
          meta: { title: "考核规则", menuKey: "admin_assessment_rules" as const },
        },
        {
          path: "admin/scoring-schemes",
          component: ScoringSchemeManageView,
          meta: { title: "评分方案", menuKey: "admin_scoring_schemes" as const },
        },
        {
          path: "admin/employees",
          component: EmployeeManageView,
          meta: { title: "员工管理", menuKey: "admin_employees" as const },
        },
        {
          path: "admin/departments",
          component: DepartmentManageView,
          meta: { title: "部门管理", menuKey: "admin_departments" as const },
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
          meta: { title: "系统配置", menuKey: "admin_performance_feishu_task" as const },
        },
        {
          path: "admin/api-tokens",
          component: ApiTokenManageView,
          meta: { title: "API Token", menuKey: "admin_api_tokens" as const },
        },
        {
          path: "admin/performance-feishu-task",
          redirect: "/admin/system-config",
        },
      ],
    },
    {
      path: "/:pathMatch(.*)*",
      name: "notfound",
      component: NotFoundView,
      meta: { bare: true, title: "页面不存在" },
    },
  ],
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  const authLogin = useAuthLoginStore();
  if (!session.loaded) {
    await session.bootstrap();
  }
  if (to.name === "login" || to.name === "feishu-login-entry") {
    await authLogin.refresh();
  }
  if ((to.name === "login" || to.name === "feishu-login-entry") && session.loggedIn) {
    const next =
      typeof to.query.next === "string" && to.query.next.startsWith("/") && !to.query.next.startsWith("//")
        ? to.query.next
        : "/todo";
    return { path: next, replace: true };
  }
  return true;
});

export default router;
