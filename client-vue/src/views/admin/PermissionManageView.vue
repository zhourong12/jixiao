<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import type { MenuPermissionKey, MenuPermissionMatrixResponse } from "@/types/api.interface";
import { MENU_PERMISSION_KEYS } from "@/types/api.interface";
import { getMenuPermissionMatrix, updateMenuPermissionsForRole } from "@/api/roles";

const MENU_LABELS: Record<MenuPermissionKey, string> = {
  todo: "待办",
  performance_list: "绩效列表",
  performance_export: "绩效导出",
  performance_list_all: "全部绩效",
  performance_batch_create: "批量创建绩效",
  performance_review_admin: "绩效复审管理",
  admin_performance_calibration: "绩效校准",
  admin_performance_feishu_task: "飞书绩效待办",
  my_performance: "我的绩效",
  admin_templates: "模板管理",
  admin_assessment_rules: "考核规则",
  admin_scoring_schemes: "评分方案",
  admin_employees: "员工管理",
  admin_departments: "部门管理",
  admin_roles: "角色管理",
  admin_permissions: "权限管理",
  admin_statistics_months: "周期与评选",
};

const MENU_GROUPS: { label: string; keys: MenuPermissionKey[] }[] = [
  {
    label: "业务与绩效",
    keys: [
      "todo",
      "my_performance",
      "performance_list",
      "performance_list_all",
      "performance_review_admin",
      "admin_performance_calibration",
    ],
  },
  {
    label: "管理与系统",
    keys: [
      "admin_templates",
      "admin_assessment_rules",
      "admin_scoring_schemes",
      "admin_employees",
      "admin_departments",
      "admin_roles",
      "admin_permissions",
      "admin_statistics_months",
      "admin_performance_feishu_task",
    ],
  },
];

const matrix = ref<MenuPermissionMatrixResponse | null>(null);
const loading = ref(true);
const saving = ref<string | null>(null);
const activeRole = ref("");

const roleInfos = computed(() => {
  if (!matrix.value) return [];
  return (
    matrix.value.roleInfos ??
    matrix.value.roles.map((roleKey) => ({ roleKey, name: roleKey, isSystem: true }))
  );
});

const visibleGroups = computed(() => {
  const apiMenus = matrix.value?.menus;
  const catalog = new Set<MenuPermissionKey>(MENU_PERMISSION_KEYS);
  if (Array.isArray(apiMenus)) {
    for (const k of apiMenus) {
      catalog.add(k);
    }
  }
  return MENU_GROUPS.map((group) => ({
    ...group,
    keys: group.keys.filter((key) => catalog.has(key)),
  })).filter((group) => group.keys.length > 0);
});

function notifyMenuPermissionsChanged() {
  window.dispatchEvent(new CustomEvent("menu-permissions-changed"));
}

async function load() {
  loading.value = true;
  try {
    const data = await getMenuPermissionMatrix();
    matrix.value = data;
    if (data.roles.length > 0) {
      activeRole.value = activeRole.value && data.roles.includes(activeRole.value) ? activeRole.value : data.roles[0]!;
    }
  } catch {
    matrix.value = null;
  } finally {
    loading.value = false;
  }
}

const toggleError = ref("");

async function toggle(role: string, menuKey: MenuPermissionKey, checked: boolean) {
  if (role === "super_admin") return;
  saving.value = `${role}:${menuKey}`;
  toggleError.value = "";
  try {
    await updateMenuPermissionsForRole({ role, menus: { [menuKey]: checked } });
    await load();
    notifyMenuPermissionsChanged();
  } catch (err: unknown) {
    toggleError.value = err instanceof Error ? err.message : "操作失败，请重试";
  } finally {
    saving.value = null;
  }
}

function isAllowed(role: string, key: MenuPermissionKey): boolean {
  return matrix.value?.matrix[role]?.[key] === true;
}

onMounted(() => void load());
</script>

<template>
  <div class="ui-page">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p class="ui-page-intro text-base">为各角色配置可访问的菜单与功能。</p>
      </div>
      <RouterLink to="/admin/roles" class="text-sm font-medium text-primary hover:underline">角色管理</RouterLink>
    </div>
    <div v-if="loading" class="ui-loading py-20 text-base">加载中...</div>
    <div
      v-else-if="!matrix || matrix.roles.length === 0"
      class="ui-card py-16 text-center text-base text-muted-foreground"
    >
      暂无角色数据
    </div>
    <template v-else>
      <section class="ui-card space-y-4">
        <h2 class="text-lg font-semibold text-foreground">选择角色</h2>
        <div class="flex flex-wrap gap-3">
          <button
            v-for="r in roleInfos"
            :key="r.roleKey"
            type="button"
            class="min-h-11 rounded-full border px-5 py-2.5 text-base font-medium transition-colors"
            :class="
              activeRole === r.roleKey
                ? 'border-primary bg-primary text-primary-foreground shadow-sm'
                : 'border-border bg-card text-foreground hover:bg-accent'
            "
            @click="activeRole = r.roleKey"
          >
            {{ r.name }}
          </button>
        </div>
      </section>
      <p
        v-if="activeRole === 'super_admin'"
        class="ui-card text-base leading-relaxed text-muted-foreground"
      >
        超级管理员默认拥有全部菜单权限，无需单独配置。
      </p>
      <template v-else>
        <div v-if="toggleError" class="rounded-lg border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700">
          {{ toggleError }}
        </div>
        <section v-for="group in visibleGroups" :key="group.label" class="ui-card space-y-5">
          <h2 class="text-xl font-semibold text-foreground">{{ group.label }}</h2>
          <ul class="divide-y divide-border">
            <li
              v-for="key in group.keys"
              :key="key"
              class="flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <span class="text-base font-medium text-foreground">{{ MENU_LABELS[key] }}</span>
              <button
                type="button"
                class="ui-btn min-h-11 min-w-[7.5rem] px-5 text-base"
                :class="isAllowed(activeRole, key) ? 'ui-btn-primary' : 'ui-btn-outline'"
                :disabled="saving === `${activeRole}:${key}`"
                @click="toggle(activeRole, key, !isAllowed(activeRole, key))"
              >
                {{
                  saving === `${activeRole}:${key}`
                    ? "保存中..."
                    : isAllowed(activeRole, key)
                      ? "已开启"
                      : "已关闭"
                }}
              </button>
            </li>
          </ul>
        </section>
      </template>
    </template>
  </div>
</template>
