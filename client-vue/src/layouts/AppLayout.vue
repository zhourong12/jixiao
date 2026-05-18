<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import NavIcon from "@/components/NavIcon.vue";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import { NAV_ITEMS, type NavItem } from "@/constants/nav";
import { passwordLoginEnabledFromEnv } from "@/config/login";
import { useSessionStore } from "@/stores/session";
import { shortPersonDisplayName } from "@/utils/user";

const session = useSessionStore();
const route = useRoute();
const router = useRouter();
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

function isActive(item: NavItem): boolean {
  const path = activePath.value;
  const name = route.name;
  if (item.menuKey === "todo") return name === "todo" || name === "home";
  if (item.menuKey === "performance_list") {
    return name === "performances" || name === "performance-detail";
  }
  if (item.path.startsWith("/admin/")) {
    return path === item.path || path.startsWith(item.path + "/");
  }
  return path === item.path || path.startsWith(item.path + "/");
}

async function onLogout() {
  await session.logout();
  window.location.href = "/todo";
}

function goFeishu() {
  const q: Record<string, string> = {};
  if (route.path !== "/login" && route.path !== "/feishu-callback") {
    q.next = route.fullPath;
  }
  void router.push({ path: "/login", query: Object.keys(q).length ? q : {} });
}

function onMenuPermissionsChanged() {
  void session.refreshMenuPermissions(true);
}

onMounted(() => {
  window.addEventListener("menu-permissions-changed", onMenuPermissionsChanged);
});

onUnmounted(() => {
  window.removeEventListener("menu-permissions-changed", onMenuPermissionsChanged);
});
</script>

<template>
  <div class="min-h-screen bg-background">
    <aside
      class="fixed inset-y-0 left-0 z-40 flex h-screen flex-col overflow-hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-[width] duration-200"
      :class="sidebarCollapsed ? 'w-16' : 'w-56'"
    >
      <div class="border-b border-sidebar-border p-3">
        <RouterLink :to="brandPath" class="flex items-center gap-2 rounded-md p-2 hover:bg-sidebar-accent">
          <div
            class="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary text-sm font-semibold text-primary-foreground"
          >
            绩
          </div>
          <span v-if="!sidebarCollapsed" class="truncate text-sm font-semibold">绩效</span>
        </RouterLink>
      </div>
      <nav class="flex-1 space-y-0.5 overflow-y-auto p-2">
        <RouterLink
          v-for="item in visibleNav"
          :key="item.path"
          :to="item.path"
          class="flex items-center gap-2 rounded-md px-3 py-2.5 text-sm transition-colors hover:bg-sidebar-accent"
          :class="
            isActive(item) ? 'bg-sidebar-primary text-sidebar-primary-foreground' : 'text-sidebar-foreground'
          "
        >
          <NavIcon :menu-key="item.menuKey" />
          <span v-if="!sidebarCollapsed">{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="shrink-0 border-t border-sidebar-border p-2">
        <button
          type="button"
          class="mb-1 w-full rounded-md px-2 py-1 text-left text-xs text-sidebar-foreground/70 hover:bg-sidebar-accent"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          {{ sidebarCollapsed ? "展开侧栏" : "收起侧栏" }}
        </button>
        <div class="rounded-md bg-sidebar-accent/50 p-2 text-xs">
          <p class="truncate font-medium text-sidebar-foreground">
            {{
              session.loggedIn
                ? shortPersonDisplayName(session.name) || session.userId || "用户"
                : "未登录"
            }}
          </p>
        </div>
        <RouterLink
          v-if="!session.loggedIn && passwordLoginEnabledFromEnv"
          to="/login"
          class="mt-1 block w-full rounded-md border border-sidebar-border px-3 py-2 text-center text-sm hover:bg-sidebar-accent"
        >
          账号登录
        </RouterLink>
        <button
          v-if="session.loggedIn"
          type="button"
          class="mt-2 w-full rounded-md border border-sidebar-border px-3 py-2 text-sm hover:bg-sidebar-accent"
          @click="onLogout"
        >
          退出登录
        </button>
        <button
          v-else
          type="button"
          class="mt-2 w-full rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground hover:opacity-90"
          :class="passwordLoginEnabledFromEnv ? '' : 'mt-1'"
          @click="goFeishu"
        >
          飞书登录
        </button>
      </div>
    </aside>
    <div
      class="flex min-h-screen min-w-0 flex-col transition-[margin] duration-200 ease-out"
      :class="sidebarCollapsed ? 'ml-16' : 'ml-56'"
    >
      <header class="flex h-16 shrink-0 items-center justify-between border-b border-border bg-card px-6 shadow-sm">
        <div class="min-w-0">
          <h1 class="truncate text-xl font-semibold text-foreground">{{ pageTitle }}</h1>
        </div>
        <UserDisplay
          v-if="session.userId"
          :value="{ user_id: session.userId, name: session.name }"
          size="small"
        />
      </header>
      <main class="min-h-0 flex-1 overflow-auto bg-background p-6 md:p-8">
        <div class="mx-auto w-full max-w-[1400px]">
          <RouterView />
        </div>
      </main>
    </div>
  </div>
</template>
