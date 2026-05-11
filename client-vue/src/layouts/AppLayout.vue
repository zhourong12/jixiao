<script setup lang="ts">
import { computed, ref } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";
import { NAV_ITEMS } from "@/constants/nav";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const route = useRoute();
const sidebarCollapsed = ref(false);

const pageTitle = computed(() => {
  for (let i = route.matched.length - 1; i >= 0; i--) {
    const t = route.matched[i]?.meta?.title;
    if (typeof t === "string" && t) return t;
  }
  return " ";
});

const visibleNav = computed(() => NAV_ITEMS.filter((i) => session.allow(i.menuKey)));

const brandPath = computed(() => visibleNav.value[0]?.path ?? "/");

const activePath = computed(() => route.path);

function isActive(path: string): boolean {
  if (path === "/") return activePath.value === "/";
  return activePath.value === path || activePath.value.startsWith(path + "/");
}

async function onLogout() {
  await session.logout();
  window.location.href = "/";
}

function goFeishu() {
  window.location.href = "/auth/feishu/login";
}
</script>

<template>
  <div class="flex min-h-screen bg-background">
    <aside
      class="flex w-56 shrink-0 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-all"
      :class="sidebarCollapsed ? 'w-16' : 'w-56'"
    >
      <div class="border-b border-sidebar-border p-3">
        <RouterLink :to="brandPath" class="flex items-center gap-2 rounded-md p-2 hover:bg-sidebar-accent">
          <div
            class="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-sm font-semibold text-primary-foreground"
          >
            ?
          </div>
          <span v-if="!sidebarCollapsed" class="truncate text-sm font-semibold">??????</span>
        </RouterLink>
      </div>
      <nav class="flex-1 space-y-0.5 overflow-y-auto p-2">
        <RouterLink
          v-for="item in visibleNav"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-2 rounded-md px-3 py-2.5 text-sm transition-colors hover:bg-sidebar-accent"
          :class="
            isActive(item.path) ? 'bg-sidebar-primary text-sidebar-primary-foreground' : 'text-sidebar-foreground'
          "
        >
          <span class="size-4 shrink-0 rounded bg-current/20 opacity-80" />
          <span v-if="!sidebarCollapsed">{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="border-t border-sidebar-border p-2">
        <button
          type="button"
          class="mb-1 w-full rounded-md px-2 py-1 text-left text-xs text-sidebar-foreground/70 hover:bg-sidebar-accent"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          {{ sidebarCollapsed ? "????" : "????" }}
        </button>
        <div class="rounded-md bg-sidebar-accent/50 p-2 text-xs">
          <p class="truncate font-medium">{{ session.name || "??" }}</p>
          <p class="text-sidebar-foreground/60">{{ session.loggedIn ? "???" : "???" }}</p>
        </div>
        <RouterLink
          v-if="!session.loggedIn"
          to="/login"
          class="mt-1 block w-full rounded-md border border-sidebar-border px-3 py-2 text-center text-sm hover:bg-sidebar-accent"
        >
          ????
        </RouterLink>
        <button
          v-if="session.loggedIn"
          type="button"
          class="mt-2 w-full rounded-md border border-sidebar-border px-3 py-2 text-sm hover:bg-sidebar-accent"
          @click="onLogout"
        >
          ????
        </button>
        <button
          v-else
          type="button"
          class="mt-2 w-full rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground hover:opacity-90"
          @click="goFeishu"
        >
          ????
        </button>
      </div>
    </aside>
    <div class="flex min-w-0 flex-1 flex-col">
      <header class="flex h-14 items-center justify-between border-b border-border bg-card px-6 shadow-sm">
        <h1 class="text-base font-semibold text-foreground">{{ pageTitle }}</h1>
      </header>
      <main class="flex-1 overflow-auto p-6">
        <RouterView />
      </main>
    </div>
  </div>
</template>
