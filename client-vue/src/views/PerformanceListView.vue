<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { PerformanceExportItem, PerformanceListItem, PerformanceStatus } from "@/types/api.interface";
import { exportPerformances, listPerformances, deletePerformance } from "@/api/performances";
import CreatePerformanceDialog from "@/components/CreatePerformanceDialog.vue";
import DepartmentTreeSelect from "@/components/business-ui/DepartmentTreeSelect.vue";
import { parseDepartmentFilter } from "@/utils/departmentFilter";
import SearchableMultiSelect from "@/components/ui/SearchableMultiSelect.vue";
import SearchableSelect from "@/components/ui/SearchableSelect.vue";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import PerformanceStatusBadge from "@/components/ui/PerformanceStatusBadge.vue";
import ListPagination from "@/components/ui/ListPagination.vue";
import {
  PERFORMANCE_FILTER_STATUSES,
  performanceStatusLabel,
} from "@/constants/performanceStatus";
import { usePerformanceMonthPeriodOptions } from "@/composables/usePerformanceMonthPeriodOptions";
import { formatDateTime } from "@/utils/datetime";
import { formatPeriodDisplay } from "@/utils/period";

const route = useRoute();
const router = useRouter();

const { periodSelectOptions, periodLoading, loadPeriodOptions } = usePerformanceMonthPeriodOptions();

const statusSelectOptions = computed(() =>
  PERFORMANCE_FILTER_STATUSES.map((value) => ({
    value,
    label: performanceStatusLabel(value),
  })),
);

const items = ref<PerformanceListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const loading = ref(true);
const exporting = ref(false);
const statusFilter = ref<PerformanceStatus[]>([]);
const periodFilter = ref("");
const employeeNameFilter = ref("");
const departmentFilter = ref("");
const message = ref<string | null>(null);
const canBatchCreate = ref(false);
const canExport = ref(false);
const canDelete = ref(false);
const createOpen = ref(false);

const focusParam = computed(() => {
  const f = route.query.focus;
  return f === "need_score" || f === "need_approve_goal" ? String(f) : "";
});

function escapeCsvCell(v: string): string {
  const s = String(v ?? "");
  if (/[",\r\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

function downloadPerformanceCsv(rows: PerformanceExportItem[]) {
  const headers = ["员工姓名", "部门", "周期", "状态", "总分", "等级", "更新时间"];
  const body = rows.map((r) =>
    [
      r.employeeName,
      r.department,
      periodLabel(r.period),
      performanceStatusLabel(r.status),
      formatScore(r.totalScore),
      r.scoreGrade?.trim() ? r.scoreGrade : "—",
      formatDateTime(r.updatedAt),
    ]
      .map(escapeCsvCell)
      .join(","),
  );
  const blob = new Blob([`\uFEFF${[headers.join(","), ...body].join("\r\n")}`], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `绩效导出_${new Date().toISOString().slice(0, 19).replace(/[:T]/g, "-")}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function formatScore(score?: number) {
  return score != null ? score.toFixed(1) : "-";
}

async function load() {
  loading.value = true;
  message.value = null;
  try {
    const res = await listPerformances({
      page: page.value,
      pageSize: pageSize.value,
      focus: focusParam.value || undefined,
      status: focusParam.value ? undefined : statusFilter.value.length ? statusFilter.value : undefined,
      period: periodFilter.value || undefined,
      ...parseDepartmentFilter(departmentFilter.value),
      employeeName: employeeNameFilter.value || undefined,
    });
    items.value = res.items;
    total.value = res.total;
    canBatchCreate.value = Boolean(res.canBatchCreate);
    canExport.value = Boolean(res.canExport);
    canDelete.value = Boolean(res.canDelete);
  } catch (e) {
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  void load();
}

function reset() {
  statusFilter.value = [];
  periodFilter.value = "";
  employeeNameFilter.value = "";
  departmentFilter.value = "";
  const q = { ...route.query };
  delete q.focus;
  delete q.status;
  if (page.value !== 1) {
    page.value = 1;
    void router.replace({ path: "/performances", query: q });
    return;
  }
  void router.replace({ path: "/performances", query: q });
  void load();
}

function periodLabel(period?: string): string {
  return formatPeriodDisplay(period);
}

function syncFiltersFromQuery() {
  const focus = route.query.focus;
  if (focus === "need_score" || focus === "need_approve_goal") {
    statusFilter.value = [];
    return;
  }
  const status = route.query.status;
  if (typeof status === "string" && status.trim()) {
    statusFilter.value = status.split(",").map((s) => s.trim()).filter(Boolean) as PerformanceStatus[];
  } else {
    statusFilter.value = [];
  }
}

async function handleExport() {
  exporting.value = true;
  message.value = null;
  try {
    const res = await exportPerformances({
      focus: focusParam.value || undefined,
      status: focusParam.value ? undefined : statusFilter.value.length ? statusFilter.value : undefined,
      period: periodFilter.value || undefined,
      ...parseDepartmentFilter(departmentFilter.value),
      employeeName: employeeNameFilter.value || undefined,
    });
    const rows = res.items ?? [];
    if (!rows.length) {
      message.value = "暂无数据可导出";
      return;
    }
    downloadPerformanceCsv(rows);
  } catch (e) {
    message.value = e instanceof Error ? e.message : "导出失败";
  } finally {
    exporting.value = false;
  }
}

async function remove(row: PerformanceListItem) {
  const label = row.employeeName || row.employeeId;
  if (!window.confirm(`确定删除「${label}」${periodLabel(row.period)} 的绩效记录？`)) return;
  try {
    await deletePerformance(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => {
  syncFiltersFromQuery();
  void loadPeriodOptions();
  void load();
});
watch(
  () => [route.query.focus, route.query.status, page.value, pageSize.value],
  () => {
    syncFiltersFromQuery();
    void load();
  },
);
</script>

<template>
  <div class="ui-page">
    <div v-if="canBatchCreate || canExport" class="flex flex-wrap justify-end gap-2">
        <button
          v-if="canBatchCreate"
          type="button"
          class="ui-btn-primary"
          @click="createOpen = true"
        >
          创建绩效
        </button>
        <button
          v-if="canExport"
          type="button"
          class="ui-btn-outline"
          :disabled="exporting"
          @click="handleExport"
        >
          {{ exporting ? "导出中..." : "导出数据" }}
        </button>
      </div>
    <p v-if="message" class="ui-alert-danger">{{ message }}</p>
    <section class="ui-card">
      <div
        v-if="focusParam"
        class="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-md border px-4 py-3"
        :class="focusParam === 'need_score' ? 'border-[color-mix(in_srgb,var(--warning)_35%,var(--border))] bg-[var(--warning-bg)]' : 'border-[color-mix(in_srgb,var(--info)_35%,var(--border))] bg-[var(--info-bg)]'"
      >
        <div class="flex flex-wrap items-center gap-2">
          <span class="ui-badge" :class="focusParam === 'need_score' ? 'ui-badge-warning' : 'ui-badge-info'">快捷筛选</span>
          <p class="text-sm font-medium text-foreground">
            {{ focusParam === "need_score" ? "需评分" : "需验收目标" }}
          </p>
          <p class="text-sm text-muted-foreground">
            {{ focusParam === "need_score" ? "仅展示待你评分的绩效记录" : "仅展示待你验收目标的绩效记录" }}
          </p>
        </div>
        <button type="button" class="ui-btn-outline ui-btn-sm" @click="reset">清除快捷筛选</button>
      </div>
      <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div>
          <label class="ui-label">状态</label>
          <SearchableMultiSelect
            v-model="statusFilter"
            :options="statusSelectOptions"
            :disabled="!!focusParam"
            placeholder="全部"
            empty-text="没有匹配的状态"
          />
        </div>
        <div>
          <label class="ui-label">周期</label>
          <SearchableSelect
            v-model="periodFilter"
            :options="periodSelectOptions"
            :loading="periodLoading"
            placeholder="全部"
            empty-text="没有匹配的周期"
          />
        </div>
        <div>
          <label class="ui-label">部门</label>
          <DepartmentTreeSelect v-model="departmentFilter" />
        </div>
        <div>
          <label class="ui-label">员工姓名</label>
          <input v-model="employeeNameFilter" class="ui-input" @keyup.enter="search" />
        </div>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <button type="button" class="ui-btn-primary" @click="search">查询</button>
        <button type="button" class="ui-btn-outline" @click="reset">重置</button>
      </div>
    </section>
    <section class="ui-list-panel">
      <div v-if="loading" class="ui-loading">加载中...</div>
      <div v-else-if="items.length === 0" class="ui-empty">暂无数据</div>
      <div v-else class="ui-table-wrap">
      <table class="ui-table">
        <thead>
          <tr>
            <th>员工</th>
            <th>周期</th>
            <th>状态</th>
            <th>上级</th>
            <th>总分</th>
            <th>等级</th>
            <th>更新时间</th>
            <th class="w-40">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>
              <UserDisplay
                size="small"
                :value="{ user_id: r.employeeId, name: r.employeeName || r.employeeId }"
              />
            </td>
            <td>
              <span class="ui-period" :title="r.period">{{ periodLabel(r.period) }}</span>
            </td>
            <td>
              <PerformanceStatusBadge :status="r.status" />
            </td>
            <td>
              <UserDisplay
                size="small"
                :value="{ user_id: r.managerId, name: r.managerName || r.managerId }"
              />
            </td>
            <td class="font-semibold">{{ formatScore(r.totalScore) }}</td>
            <td>{{ r.scoreGrade ?? "—" }}</td>
            <td class="text-muted-foreground">{{ formatDateTime(r.updatedAt) }}</td>
            <td>
              <div class="flex flex-wrap items-center gap-2">
                <button type="button" class="ui-btn-ghost ui-btn-sm" @click="router.push(`/performances/${r.id}`)">
                  详情
                </button>
                <button
                  v-if="canDelete"
                  type="button"
                  class="text-destructive hover:underline text-sm"
                  @click="remove(r)"
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
        </table>
      </div>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </section>
    <CreatePerformanceDialog v-model:open="createOpen" @success="load" />
  </div>
</template>
