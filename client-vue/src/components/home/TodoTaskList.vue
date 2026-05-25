<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";
import type { TodoItem } from "@/types/api.interface";
import {
  getTodoMeta,
  groupTodosByBucket,
  todoActionClass,
  todoBadgeClass,
  todoRowClass,
} from "@/constants/todoStatus";
import { formatPeriodDisplay } from "@/utils/period";
import { shortPersonDisplayName } from "@/utils/user";
import { useSessionStore } from "@/stores/session";

const props = withDefaults(
  defineProps<{
    items: TodoItem[];
    emptyText?: string;
    /** card：卡片列表；table：待办表格（含查看操作） */
    layout?: "card" | "table";
  }>(),
  {
    emptyText: "本月暂无待办",
    layout: "card",
  },
);

const session = useSessionStore();
const grouped = computed(() => groupTodosByBucket(props.items, session.userId));

const sections = computed(
  () =>
    [
      { key: "mine" as const, label: "我的待办", items: grouped.value.mine, badge: "ui-badge-warning", showCount: true },
      { key: "team" as const, label: "团队待办", items: grouped.value.team, badge: "ui-badge-info", showCount: true },
      {
        key: "flow" as const,
        label: "流程待办",
        items: grouped.value.flow,
        badge: "ui-badge-neutral",
        showCount: false,
      },
    ] as const,
);

function employeeLabel(item: TodoItem): string {
  if (item.employeeName?.trim()) return shortPersonDisplayName(item.employeeName);
  const head = item.title.split(" · ")[0]?.trim();
  return head ? shortPersonDisplayName(head) : item.title;
}
</script>

<template>
  <p v-if="items.length === 0" class="py-10 text-center text-sm text-muted-foreground">{{ emptyText }}</p>

  <template v-else-if="layout === 'table'">
    <template v-for="section in sections" :key="section.key">
      <section v-if="section.items.length > 0" class="mb-6 last:mb-0">
        <div class="mb-3 flex items-center justify-between gap-3">
          <h3 class="text-sm font-semibold text-foreground">{{ section.label }}</h3>
          <span v-if="section.showCount" class="ui-badge" :class="section.badge">
            待处理 {{ section.items.length }}
          </span>
        </div>
        <div class="ui-table-wrap">
          <table class="ui-table min-w-[720px]">
            <thead>
              <tr>
                <th>员工</th>
                <th>部门</th>
                <th>周期</th>
                <th>状态</th>
                <th class="w-28 text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in section.items" :key="item.id" class="hover:bg-accent/50">
                <td class="py-4">
                  <RouterLink
                    :to="`/performances/${item.id}`"
                    class="font-medium text-primary hover:underline"
                  >
                    {{ employeeLabel(item) }}
                  </RouterLink>
                </td>
                <td class="py-4 text-muted-foreground">{{ item.departmentName?.trim() || "—" }}</td>
                <td class="py-4 text-muted-foreground">{{ formatPeriodDisplay(item.period) }}</td>
                <td class="py-4">
                  <span class="ui-badge" :class="todoBadgeClass(item.type)">{{ getTodoMeta(item.type).label }}</span>
                </td>
                <td class="py-4">
                  <div class="flex items-center justify-end gap-2">
                    <RouterLink
                      :to="`/performances/${item.id}`"
                      class="ui-btn-ghost ui-btn-sm inline-flex size-11 items-center justify-center p-0 text-muted-foreground hover:text-foreground"
                      aria-label="查看详情"
                      title="查看"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" class="size-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                        <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                      </svg>
                    </RouterLink>
                    <RouterLink
                      :to="`/performances/${item.id}`"
                      class="shrink-0"
                      :class="todoActionClass(item.type)"
                    >
                      {{ getTodoMeta(item.type).actionLabel }}
                    </RouterLink>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
    <p
      v-if="!grouped.mine.length && !grouped.team.length && !grouped.flow.length"
      class="py-10 text-center text-sm text-muted-foreground"
    >
      {{ emptyText }}
    </p>
  </template>

  <div v-else class="space-y-6">
    <section v-for="section in sections" :key="section.key">
      <template v-if="section.items.length > 0">
        <div class="mb-3 flex items-center justify-between gap-3">
          <h3 class="text-sm font-semibold text-foreground">{{ section.label }}</h3>
          <span v-if="section.showCount" class="ui-badge" :class="section.badge">
            待处理 {{ section.items.length }}
          </span>
        </div>
        <ul class="space-y-3">
          <li v-for="item in section.items" :key="item.id">
            <RouterLink :to="`/performances/${item.id}`" class="ui-todo-link" :class="todoRowClass(item.type)">
              <div class="min-w-0 flex-1">
                <div class="mb-2">
                  <span class="ui-badge" :class="todoBadgeClass(item.type)">{{ getTodoMeta(item.type).label }}</span>
                </div>
                <p class="truncate text-base font-semibold text-foreground">{{ employeeLabel(item) }}</p>
                <p class="mt-1 text-sm text-muted-foreground">周期：{{ formatPeriodDisplay(item.period) }}</p>
              </div>
              <span class="shrink-0" :class="todoActionClass(item.type)">{{ getTodoMeta(item.type).actionLabel }}</span>
            </RouterLink>
          </li>
        </ul>
      </template>
    </section>
  </div>
</template>
