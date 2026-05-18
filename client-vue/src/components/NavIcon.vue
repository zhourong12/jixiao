<script setup lang="ts">
import type { MenuPermissionKey } from "@/types/api.interface";

defineProps<{ menuKey: MenuPermissionKey }>();
</script>

<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="size-4 shrink-0"
    aria-hidden="true"
  >
    <template v-if="menuKey === 'todo'">
      <path d="M9 5h11M9 12h11M9 19h11M4 5h.01M4 12h.01M4 19h.01" />
    </template>
    <template v-else-if="menuKey === 'my_performance'">
      <path d="M20 21a8 8 0 1 0-16 0" />
      <circle cx="12" cy="7" r="4" />
    </template>
    <template v-else-if="menuKey === 'performance_list'">
      <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
    </template>
    <template v-else-if="menuKey === 'admin_performance_calibration'">
      <path d="M12 3v18M3 12h18" />
    </template>
    <template v-else-if="menuKey === 'admin_templates'">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6" />
    </template>
    <template v-else-if="menuKey === 'admin_assessment_rules'">
      <path d="M12 3v18M3 12h18" />
      <path d="M7 15h10M7 9h6" />
    </template>
    <template v-else-if="menuKey === 'admin_employees'">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
    </template>
    <template v-else-if="menuKey === 'admin_departments'">
      <path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6" />
    </template>
    <template v-else-if="menuKey === 'admin_roles'">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 11h-6" />
    </template>
    <template v-else-if="menuKey === 'admin_permissions'">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
    </template>
    <template v-else-if="menuKey === 'admin_statistics_months'">
      <path d="M3 3v18h18" />
      <path d="M7 16V9M12 16V5M17 16v-4" />
    </template>
    <template v-else-if="menuKey === 'admin_performance_feishu_task'">
      <path d="M9 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2z" />
      <path d="M19 4h-4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z" />
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
    </template>
  </svg>
</template>
