<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { HomeActionCounts, TodoItem } from "@/types/api.interface";
import { getHomeActionCounts, getTodos } from "@/api/home";
import { useSessionStore } from "@/stores/session";

const router = useRouter();
const session = useSessionStore();
const todos = ref<TodoItem[]>([]);
const counts = ref<HomeActionCounts | null>(null);
const loading = ref(true);

onMounted(async () => {
  loading.value = true;
  try {
    const n = new Date();
    const y = n.getFullYear();
    const m = n.getMonth() + 1;
    const [t, c] = await Promise.all([getTodos({ year: y, month: m }), getHomeActionCounts({ year: y, month: m })]);
    todos.value = t.items;
    counts.value = c;
  } catch {
    todos.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-foreground">??</h1>
      <p class="mt-1 text-sm text-muted-foreground">
        ???<span class="font-medium">{{ session.name || "??" }}</span> ? ???????????????????
      </p>
    </div>
    <div v-if="loading" class="flex h-40 items-center justify-center text-muted-foreground">???...</div>
    <template v-else>
      <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <button
          type="button"
          class="flex items-center justify-between rounded-md border border-border bg-primary px-4 py-4 text-left text-primary-foreground shadow-sm hover:opacity-95"
          @click="router.push('/my-performance')"
        >
          <span>????</span>
          <span class="opacity-80">?</span>
        </button>
        <button
          type="button"
          class="flex items-center justify-between rounded-md border border-border bg-card px-4 py-4 text-left shadow-sm hover:bg-accent"
          @click="router.push('/performances?focus=need_score')"
        >
          <span>????</span>
          <span class="text-muted-foreground">{{ counts?.needScore ?? 0 }}</span>
        </button>
        <button
          type="button"
          class="flex items-center justify-between rounded-md border border-border bg-card px-4 py-4 text-left shadow-sm hover:bg-accent"
          @click="router.push('/performances?focus=need_approve_goal')"
        >
          <span>?????</span>
          <span class="text-muted-foreground">{{ counts?.needApproveGoal ?? 0 }}</span>
        </button>
      </div>
      <div class="rounded-md border border-border bg-card shadow-sm">
        <div class="border-b border-border px-4 py-3 font-semibold">????</div>
        <ul class="divide-y divide-border">
          <li
            v-for="t in todos"
            :key="t.id"
            class="cursor-pointer px-4 py-3 hover:bg-accent"
            @click="router.push(`/performances/${t.id}`)"
          >
            <p class="text-sm font-medium">{{ t.title }}</p>
            <p class="text-xs text-muted-foreground">{{ t.period }}</p>
          </li>
          <li v-if="todos.length === 0" class="px-4 py-10 text-center text-sm text-muted-foreground">????</li>
        </ul>
      </div>
    </template>
  </div>
</template>
