<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import NavIcon from "@/components/NavIcon.vue";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import { NAV_ITEMS, type NavItem } from "@/constants/nav";
import { usePasswordLoginEnabled } from "@/composables/usePasswordLoginEnabled";
import { useSessionStore } from "@/stores/session";
import { useFeishuAppBadgeOnMount } from "@/composables/useFeishuAppBadge";
import { shortPersonDisplayName } from "@/utils/user";

const session = useSessionStore();
const { passwordLoginEnabled, loaded: passwordLoginLoaded } = usePasswordLoginEnabled();
useFeishuAppBadgeOnMount();
const route = useRoute();
const router = useRouter();
const sidebarCollapsed = ref(false);
const mobileOpen = ref(false);

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

watch(() => route.path, () => { mobileOpen.value = false; });

onMounted(() => {
  window.addEventListener("menu-permissions-changed", onMenuPermissionsChanged);
});

onUnmounted(() => {
  window.removeEventListener("menu-permissions-changed", onMenuPermissionsChanged);
});
</script>

<template>
  <div class="min-h-screen bg-background">
    <!-- mobile overlay -->
    <div
      v-if="mobileOpen"
      class="fixed inset-0 z-40 bg-black/50 md:hidden"
      @click="mobileOpen = false"
    />
    <aside
      class="fixed inset-y-0 left-0 z-50 flex h-screen w-56 flex-col overflow-hidden border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-transform duration-200 md:z-40 md:translate-x-0 md:transition-[width]"
      :class="[
        mobileOpen ? 'translate-x-0' : '-translate-x-full',
        sidebarCollapsed ? 'md:w-16' : 'md:w-56',
      ]"
    >
      <div class="border-b border-sidebar-border p-3">
        <RouterLink :to="brandPath" class="flex items-center gap-2 rounded-md p-2 hover:bg-sidebar-accent">
          <img src="/app-logo.png" alt="科臻赛绩效" class="size-8 shrink-0 rounded-lg object-contain" />
          <span v-if="!sidebarCollapsed" class="truncate text-sm font-semibold">科臻赛绩效</span>
        </RouterLink>
      </div>
      <nav class="flex-1 space-y-0.5 overflow-y-auto p-2">
        <RouterLink
          v-for="item in visibleNav"
          :key="item.path"
          :to="item.path"
          class="relative flex items-center gap-2 rounded-md px-3 py-2.5 text-sm transition-colors hover:bg-sidebar-accent"
          :class="
            isActive(item)
              ? 'bg-sidebar-primary font-semibold text-sidebar-primary-foreground shadow-md ring-1 ring-white/20'
              : 'text-sidebar-foreground/80 hover:text-sidebar-foreground'
          "
          :aria-current="isActive(item) ? 'page' : undefined"
        >
          <NavIcon :menu-key="item.menuKey" />
          <span v-if="!sidebarCollapsed">{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="shrink-0 border-t border-sidebar-border p-2">
        <button
          type="button"
          class="mb-1 hidden w-full rounded-md px-2 py-1 text-left text-xs text-sidebar-foreground/70 hover:bg-sidebar-accent md:block"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          {{ sidebarCollapsed ? "展开" : "收起侧栏" }}
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
          v-if="!session.loggedIn && passwordLoginLoaded && passwordLoginEnabled"
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
          :class="passwordLoginLoaded && passwordLoginEnabled ? '' : 'mt-1'"
          @click="goFeishu"
        >
          飞书登录
        </button>
      </div>
    </aside>
    <div
      class="flex min-h-screen min-w-0 flex-col transition-[margin] duration-200 ease-out"
      :class="[sidebarCollapsed ? 'md:ml-16' : 'md:ml-56']"
    >
      <header class="flex h-14 shrink-0 items-center justify-between border-b border-border bg-card px-4 shadow-sm md:h-16 md:px-6">
        <div class="flex min-w-0 items-center gap-3">
          <button
            type="button"
            class="inline-flex size-10 items-center justify-center rounded-md text-foreground hover:bg-accent md:hidden"
            @click="mobileOpen = !mobileOpen"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="size-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
          <h1 class="truncate text-lg font-semibold text-foreground md:text-xl">{{ pageTitle }}</h1>
        </div>
        <UserDisplay
          v-if="session.userId"
          :value="{ user_id: session.userId, name: session.name }"
          size="small"
        />
      </header>
      <main class="min-h-0 flex-1 overflow-auto bg-background p-4 md:p-6 lg:p-8">
        <div class="mx-auto w-full max-w-[1400px]">
          <RouterView />
        </div>
      </main>
    </div>
  </div>
</template>
