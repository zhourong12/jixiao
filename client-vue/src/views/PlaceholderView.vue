<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import type { MenuPermissionKey } from "@/types/api.interface";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const session = useSessionStore();

const title = computed(() => (typeof route.meta.title === "string" ? route.meta.title : "管理"));
const menuKey = computed(() => route.meta.menuKey as MenuPermissionKey | undefined);

const allowed = computed(() => (menuKey.value ? session.allow(menuKey.value) : true));
</script>

<template>
  <div v-if="!allowed" class="rounded-md border border-border bg-card p-8 text-center text-sm text-muted-foreground">
    当前角色无此页面权限
  </div>
  <div v-else class="space-y-4">
    <h1 class="text-xl font-semibold text-foreground">{{ title }}</h1>
    <p class="text-sm text-muted-foreground">
      该模块界面正在从�?React�?code class="rounded bg-muted px-1">client/src</code>）迁移到 Vue。接口仍走现�?      <code class="rounded bg-muted px-1">/api</code>，可直接对照原页面逻辑在此目录下补全组件�?    </p>
  </div>
</template>
