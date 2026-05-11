<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { MenuPermissionKey, MenuPermissionMatrixResponse } from "@/types/api.interface";
import { getMenuPermissionMatrix, updateMenuPermissionsForRole } from "@/api/roles";

const MENU_LABELS: Record<MenuPermissionKey, string> = {
  todo: "??",
  home: "???",
  performance_list: "????",
  performance_export: "??????",
  performance_list_all: "??????",
  performance_batch_create: "??????",
  performance_review_admin: "???????",
  admin_performance_calibration: "????",
  my_performance: "????",
  admin_templates: "????",
  admin_notifications: "????",
  admin_employees: "????",
  admin_roles: "????",
  admin_permissions: "????",
  admin_statistics_months: "?????",
  admin_system_config: "????",
};

const MENU_GROUPS: { label: string; keys: MenuPermissionKey[] }[] = [
  {
    label: "????",
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
    label: "????",
    keys: [
      "home",
      "admin_templates",
      "admin_notifications",
      "admin_employees",
      "admin_roles",
      "admin_permissions",
      "admin_statistics_months",
      "admin_system_config",
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

async function toggle(role: string, menuKey: MenuPermissionKey, checked: boolean) {
  if (role === "super_admin") return;
  saving.value = `${role}:${menuKey}`;
  try {
    await updateMenuPermissionsForRole({ role, menus: { [menuKey]: checked } });
    await load();
  } finally {
    saving.value = null;
  }
}

function isAllowed(role: string, key: MenuPermissionKey): boolean {
  return matrix.value?.matrix[role]?.[key] !== false;
}

onMounted(() => void load());
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-bold">????</h1>
      <p class="mt-1 text-sm text-muted-foreground">?????????????</p>
    </div>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">???...</div>
    <div
      v-else-if="!matrix || matrix.roles.length === 0"
      class="rounded-md border border-border bg-card p-8 text-center text-sm text-muted-foreground"
    >
      ???????
    </div>
    <template v-else>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="r in roleInfos"
          :key="r.roleKey"
          type="button"
          class="rounded-full border px-3 py-1 text-sm"
          :class="activeRole === r.roleKey ? 'border-primary bg-primary/10' : 'border-border'"
          @click="activeRole = r.roleKey"
        >
          {{ r.name }}
        </button>
      </div>
      <div v-for="group in MENU_GROUPS" :key="group.label" class="rounded-md border border-border bg-card p-4 shadow-sm">
        <h2 class="mb-3 text-base font-semibold">{{ group.label }}</h2>
        <ul class="space-y-2">
          <li v-for="key in group.keys" :key="key" class="flex items-center justify-between gap-4 text-sm">
            <span>{{ MENU_LABELS[key] }}</span>
            <label class="inline-flex items-center gap-2">
              <input
                type="checkbox"
                :checked="isAllowed(activeRole, key)"
                :disabled="activeRole === 'super_admin' || saving === `${activeRole}:${key}`"
                @change="toggle(activeRole, key, ($event.target as HTMLInputElement).checked)"
              />
            </label>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>
