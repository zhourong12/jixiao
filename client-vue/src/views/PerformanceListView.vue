<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { PerformanceListItem, PerformanceStatus } from "@/types/api.interface";
import { listPerformances } from "@/api/performances";
import { PERFORMANCE_STATUS_LABELS } from "@/constants/performanceStatus";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const items = ref<PerformanceListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(true);
const statusFilter = ref<PerformanceStatus | "">("");
const periodFilter = ref("");
const employeeNameFilter = ref("");
const departmentId = ref("");
const message = ref<string | null>(null);

const focusParam = computed(() => {
  const f = route.query.focus;
  return f === "need_score" || f === "need_approve_goal" ? String(f) : "";
});

const canBatchCreate = computed(() => session.allow("performance_batch_create"));
const canExport = computed(() => session.allow("performance_export"));

async function load() {
  loading.value = true;
  message.value = null;
  try {
    const res = await listPerformances({
      page: page.value,
      pageSize,
      focus: focusParam.value || undefined,
      status: focusParam.value ? undefined : statusFilter.value || undefined,
      period: periodFilter.value || undefined,
      departmentId: departmentId.value || undefined,
      employeeName: employeeNameFilter.value || undefined,
    });
    items.value = res.items;
    total.value = res.total;
  } catch (e) {
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "load failed";
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  void load();
}

function reset() {
  statusFilter.value = "";
  periodFilter.value = "";
  employeeNameFilter.value = "";
  departmentId.value = "";
  page.value = 1;
  const q = { ...route.query };
  delete q.focus;
  void router.replace({ path: "/performances", query: q });
}

function statusLabel(status: PerformanceStatus): string {
  return PERFORMANCE_STATUS_LABELS[status] || status;
}

onMounted(() => void load());
watch(() => [route.query.focus, route.query.status, page.value], () => void load());
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <h1 class="text-2xl font-bold text-foreground">绩效列表</h1>
      <div v-if="canBatchCreate || canExport" class="flex gap-2 text-sm text-muted-foreground">
        <span v-if="canBatchCreate">批量创建需管理员权限</span>
        <span v-if="canExport">导出需超级管理员权限</span>
      </div>
    </div>
    <p v-if="message" class="text-sm text-destructive">{{ message }}</p>
    <div class="rounded-md border border-border bg-card p-4 shadow-sm">
      <p v-if="focusParam === 'need_score'" class="mb-3 rounded-md bg-accent px-3 py-2 text-sm">当前筛选：需我评分</p>
      <p v-if="focusParam === 'need_approve_goal'" class="mb-3 rounded-md bg-accent px-3 py-2 text-sm">
        当前筛选：需我审核目标
      </p>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="mb-1 block text-sm">状态</label>
          <select v-model="statusFilter" class="rounded-md border px-3 py-2 text-sm">
            <option value="">全部</option>
            <option v-for="(label, key) in PERFORMANCE_STATUS_LABELS" :key="key" :value="key">{{ label }}</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm">周期</label>
          <input v-model="periodFilter" class="w-32 rounded-md border px-3 py-2 text-sm" placeholder="2026-Q1" />
        </div>
        <div>
          <label class="mb-1 block text-sm">部门 ID</label>
          <input v-model="departmentId" class="w-36 rounded-md border px-3 py-2 text-sm" />
        </div>
        <div>
          <label class="mb-1 block text-sm">员工姓名</label>
          <input v-model="employeeNameFilter" class="w-36 rounded-md border px-3 py-2 text-sm" @keyup.enter="search" />
        </div>
        <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="search">
          查询
        </button>
        <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="reset">重置</button>
      </div>
    </div>
    <p class="text-sm text-muted-foreground">共 {{ total }} 条</p>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">加载中...</div>
    <div v-else class="overflow-x-auto rounded-md border border-border">
      <table class="w-full min-w-[800px] text-left text-sm">
        <thead class="border-b border-border bg-muted/40">
          <tr>
            <th class="px-3 py-2 font-medium">员工</th>
            <th class="px-3 py-2 font-medium">周期</th>
            <th class="px-3 py-2 font-medium">状态</th>
            <th class="px-3 py-2 font-medium">上级</th>
            <th class="px-3 py-2 font-medium">总分</th>
            <th class="px-3 py-2 font-medium" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id" class="border-b border-border hover:bg-accent/50">
            <td class="px-3 py-2">{{ r.employeeName || r.employeeId }}</td>
            <td class="px-3 py-2">{{ r.period }}</td>
            <td class="px-3 py-2">{{ statusLabel(r.status) }}</td>
            <td class="px-3 py-2">{{ r.managerName || r.managerId }}</td>
            <td class="px-3 py-2">{{ r.totalScore ?? "-" }}</td>
            <td class="px-3 py-2">
              <button type="button" class="text-primary hover:underline" @click="router.push(`/performances/${r.id}`)">
                详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!loading && items.length === 0" class="text-center text-sm text-muted-foreground">暂无数据</div>
    <div v-if="total > pageSize" class="flex gap-2">
      <button type="button" class="rounded-md border px-3 py-1 text-sm" :disabled="page <= 1" @click="page--">
        上一页
      </button>
      <button
        type="button"
        class="rounded-md border px-3 py-1 text-sm"
        :disabled="page * pageSize >= total"
        @click="page++"
      >
        下一页
      </button>
    </div>
  </div>
</template>
