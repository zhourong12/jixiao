<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { TodoItem } from "@/types/api.interface";
import { getTodos } from "@/api/home";
import TodoTaskList from "@/components/home/TodoTaskList.vue";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import { countTodosForBadge } from "@/constants/todoStatus";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const todos = ref<TodoItem[]>([]);
const loading = ref(true);
const badgeCount = computed(() => countTodosForBadge(todos.value));

onMounted(async () => {
  loading.value = true;
  try {
    const n = new Date();
    const y = n.getFullYear();
    const m = n.getMonth() + 1;
    const t = await getTodos({ year: y, month: m });
    todos.value = t.items;
  } catch {
    todos.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="ui-page">
    <p class="ui-page-intro">
      你好，
      <UserDisplay
        v-if="session.userId"
        :value="{ user_id: session.userId, name: session.name }"
        size="small"
        class="inline-flex align-middle"
      />
      <span v-else>同事</span>
      · 以下为与当前自然月相关的待办事项。
    </p>
    <div v-if="loading" class="ui-loading">加载中...</div>
    <template v-else>
      <div class="rounded-md border border-border bg-card shadow-sm">
        <div class="border-b border-border px-4 py-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <h2 class="text-base font-semibold">待办任务</h2>
            <span class="ui-badge ui-badge-warning">待处理 {{ badgeCount }}</span>
          </div>
          <p class="mt-1 text-xs text-muted-foreground">
            仅展示当前仍需你处理的事项；「流程待办」（计划执行、待校准）不计入角标与上方待处理数。
          </p>
        </div>
        <div class="p-3 md:p-4">
          <div class="md:hidden">
            <TodoTaskList :items="todos" layout="card" />
          </div>
          <div class="hidden md:block">
            <TodoTaskList :items="todos" layout="table" />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
