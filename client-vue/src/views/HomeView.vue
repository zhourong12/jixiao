<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { TodoItem } from "@/types/api.interface";
import { getOverview, getTodos } from "@/api/home";
import { useSessionStore } from "@/stores/session";

const PENDING = new Set([
  "template_selection",
  "goal_setting",
  "goal_rejected",
  "goal_pending_review",
  "self_review",
  "manager_review",
  "dual_manager_review",
  "dotted_manager_review",
  "final_review",
]);

const STATUS_LABEL: Record<string, string> = {
  template_selection: "选择模板",
  goal_setting: "目标设定",
  goal_rejected: "目标驳回",
  goal_pending_review: "审核目标",
  self_review: "自评",
  manager_review: "主管评分",
  dual_manager_review: "主管并行评分",
  dotted_manager_review: "虚线主管评分",
  final_review: "终审",
};

const router = useRouter();
const session = useSessionStore();
const tab = ref<"total" | "pending" | "completed" | "rejected">("total");
const todos = ref<TodoItem[]>([]);
const overview = ref<{ total: number; pending: number; completed: number; rejected: number } | null>(null);
const loading = ref(true);

const ymStr = ref(
  (() => {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, "0")}`;
  })(),
);

function partsFromYm(s: string): { year: number; month: number } {
  const [ys, ms] = s.split("-");
  const year = parseInt(ys, 10);
  const month = parseInt(ms, 10);
  if (!Number.isFinite(year) || !Number.isFinite(month)) {
    const n = new Date();
    return { year: n.getFullYear(), month: n.getMonth() + 1 };
  }
  return { year, month };
}

const yearMonth = computed(() => partsFromYm(ymStr.value));

async function load() {
  loading.value = true;
  try {
    const { year, month } = yearMonth.value;
    const [tRes, oRes] = await Promise.all([getTodos({ year, month }), getOverview({ year, month })]);
    todos.value = tRes.items;
    overview.value = oRes;
  } catch {
    todos.value = [];
    overview.value = null;
  } finally {
    loading.value = false;
  }
}

watch(ymStr, () => void load(), { immediate: true });

const displayedTodos = computed(() => {
  if (tab.value === "pending") return todos.value.filter((t) => PENDING.has(t.type));
  if (tab.value === "total") return todos.value;
  return [];
});

watch(
  () => session.permLoading,
  (v) => {
    if (!v && !session.allow("home")) router.replace("/todo");
  },
  { immediate: true },
);
</script>

<template>
  <div v-if="session.permLoading || loading" class="flex h-64 items-center justify-center text-muted-foreground">
    加载中�?  </div>
  <div v-else-if="!session.allow('home')" />
  <div v-else class="space-y-6">
    <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-foreground">绩效工作�?/h1>
        <p class="mt-1 text-sm text-muted-foreground">
          欢迎�?span class="font-medium text-foreground">{{ session.name }}</span>
        </p>
      </div>
      <div class="flex flex-col gap-1.5 sm:w-56">
        <label class="text-sm font-medium text-foreground">统计月份</label>
        <input
          v-model="ymStr"
          type="month"
          class="h-9 w-full rounded-md border border-border bg-background px-3 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
        />
        <p class="text-xs text-muted-foreground">指标与待办按该月内「最后更新时间」筛�?/p>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
      <button
        type="button"
        class="flex flex-col gap-0.5 rounded-md border border-border bg-card p-3 text-left shadow-sm hover:bg-accent"
        :class="tab === 'total' ? 'ring-2 ring-primary' : ''"
        @click="tab = 'total'"
      >
        <span class="text-xs text-muted-foreground">总绩�?/span>
        <span class="text-lg font-bold tabular-nums">{{ overview?.total ?? 0 }}</span>
      </button>
      <button
        type="button"
        class="flex flex-col gap-0.5 rounded-md border border-border bg-card p-3 text-left shadow-sm hover:bg-accent"
        :class="tab === 'pending' ? 'ring-2 ring-primary' : ''"
        @click="tab = 'pending'"
      >
        <span class="text-xs text-muted-foreground">待处�?/span>
        <span class="text-lg font-bold tabular-nums">{{ overview?.pending ?? 0 }}</span>
      </button>
      <button
        type="button"
        class="flex flex-col gap-0.5 rounded-md border border-border bg-card p-3 text-left shadow-sm hover:bg-accent"
        :class="tab === 'completed' ? 'ring-2 ring-primary' : ''"
        @click="tab = 'completed'"
      >
        <span class="text-xs text-muted-foreground">已完�?/span>
        <span class="text-lg font-bold tabular-nums">{{ overview?.completed ?? 0 }}</span>
      </button>
      <button
        type="button"
        class="flex flex-col gap-0.5 rounded-md border border-border bg-card p-3 text-left shadow-sm hover:bg-accent"
        :class="tab === 'rejected' ? 'ring-2 ring-primary' : ''"
        @click="tab = 'rejected'"
      >
        <span class="text-xs text-muted-foreground">已驳�?/span>
        <span class="text-lg font-bold tabular-nums">{{ overview?.rejected ?? 0 }}</span>
      </button>
    </div>

    <div v-if="tab === 'total' || tab === 'pending'" class="rounded-md border border-border bg-card shadow-sm">
      <div class="border-b border-border px-4 py-3">
        <h2 class="text-base font-semibold">待办任务</h2>
      </div>
      <div class="p-4">
        <p v-if="displayedTodos.length === 0" class="py-10 text-center text-sm text-muted-foreground">
          {{ tab === "pending" ? "本月无处理中待办" : "本月无待办事�? }}
        </p>
        <ul v-else class="space-y-2">
          <li
            v-for="todo in displayedTodos"
            :key="todo.id"
            class="flex cursor-pointer items-center justify-between rounded-md border border-border p-3 hover:bg-accent"
            @click="router.push(`/performances/${todo.id}`)"
          >
            <div>
              <p class="text-sm font-medium text-foreground">{{ todo.title }}</p>
              <p class="text-xs text-muted-foreground">周期：{{ todo.period }}</p>
            </div>
            <span class="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
              {{ STATUS_LABEL[todo.type] ?? todo.type }}
            </span>
          </li>
        </ul>
      </div>
    </div>

    <div v-else-if="tab === 'completed'" class="rounded-md border border-border bg-card p-10 text-center shadow-sm">
      <p class="text-sm text-muted-foreground">
        本月已完�?<span class="font-semibold text-foreground">{{ overview?.completed ?? 0 }}</span> �?      </p>
      <button
        type="button"
        class="mt-4 rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent"
        @click="router.push('/performances?status=completed')"
      >
        在绩效列表中查看
      </button>
    </div>

    <div v-else class="rounded-md border border-border bg-card p-10 text-center shadow-sm">
      <p class="text-sm text-muted-foreground">
        本月目标被驳�?<span class="font-semibold text-foreground">{{ overview?.rejected ?? 0 }}</span> �?      </p>
      <button
        type="button"
        class="mt-4 rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent"
        @click="router.push('/performances?status=goal_rejected')"
      >
        在绩效列表中查看
      </button>
    </div>
  </div>
</template>
