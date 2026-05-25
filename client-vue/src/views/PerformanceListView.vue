<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { PerformanceExportItem, PerformanceListItem, PerformanceStatus } from "@/types/api.interface";
import {
  batchIssueSelfReview,
  exportPerformances,
  listPerformances,
  deletePerformance,
  issueSelfReview,
} from "@/api/performances";
import { useToast } from "@/composables/useToast";
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
const toast = useToast();

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
const canBatchIssueSelfReview = ref(false);
const createOpen = ref(false);
const selectedIds = ref<string[]>([]);
const batchIssuing = ref(false);
const rowIssuingId = ref<string | null>(null);

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
    canBatchIssueSelfReview.value = Boolean(res.canBatchIssueSelfReview);
    selectedIds.value = [];
  } catch (e) {
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

const selectableOnPage = computed(() => items.value.filter((r) => r.canIssueSelfReview));

const allSelectableChecked = computed({
  get() {
    const selectable = selectableOnPage.value;
    return selectable.length > 0 && selectable.every((r) => selectedIds.value.includes(r.id));
  },
  set(checked: boolean) {
    if (checked) {
      selectedIds.value = selectableOnPage.value.map((r) => r.id);
    } else {
      selectedIds.value = [];
    }
  },
});

function toggleRowSelected(id: string, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id];
  } else {
    selectedIds.value = selectedIds.value.filter((x) => x !== id);
  }
}

async function issueOne(row: PerformanceListItem) {
  if (!row.canIssueSelfReview) return;
  rowIssuingId.value = row.id;
  try {
    await issueSelfReview(row.id);
    toast.success(`已下发「${row.employeeName || row.employeeId}」员工自评`);
    await load();
  } catch (e) {
    toast.error(e instanceof Error ? e.message : "下发失败");
  } finally {
    rowIssuingId.value = null;
  }
}

async function batchIssue() {
  if (!selectedIds.value.length) {
    toast.error("请先勾选计划执行中的绩效");
    return;
  }
  batchIssuing.value = true;
  try {
    const res = await batchIssueSelfReview(selectedIds.value);
    if (res.failed.length) {
      toast.error(`成功 ${res.successCount} 条，失败 ${res.failed.length} 条`);
    } else {
      toast.success(`已下发 ${res.successCount} 条员工自评`);
    }
    await load();
  } catch (e) {
    toast.error(e instanceof Error ? e.message : "批量下发失败");
  } finally {
    batchIssuing.value = false;
  }
}

function buildListQuery(): Record<string, string> {
  const q: Record<string, string> = {};
  if (focusParam.value) {
    q.focus = focusParam.value;
  } else if (statusFilter.value.length) {
    q.status = statusFilter.value.join(",");
  }
  if (periodFilter.value.trim()) q.period = periodFilter.value.trim();
  if (departmentFilter.value.trim()) q.dept = departmentFilter.value.trim();
  if (employeeNameFilter.value.trim()) q.employeeName = employeeNameFilter.value.trim();
  if (page.value > 1) q.page = String(page.value);
  if (pageSize.value !== 10) q.pageSize = String(pageSize.value);
  return q;
}

function replaceListQuery() {
  return router.replace({ path: "/performances", query: buildListQuery() });
}

function openDetail(id: string) {
  void router.replace({ path: "/performances", query: buildListQuery() }).then(() => {
    void router.push(`/performances/${id}`);
  });
}

/** 查询按钮主动触发 load 时，避免 replace 改 URL 后 watch 再请求一次 */
let skipNextQueryLoad = false;

function search() {
  page.value = 1;
  skipNextQueryLoad = true;
  void load();
  void replaceListQuery().finally(() => {
    if (skipNextQueryLoad) skipNextQueryLoad = false;
  });
}

function reset() {
  statusFilter.value = [];
  periodFilter.value = "";
  employeeNameFilter.value = "";
  departmentFilter.value = "";
  page.value = 1;
  void router.replace({ path: "/performances", query: {} });
}

function periodLabel(period?: string): string {
  return formatPeriodDisplay(period);
}

function syncFiltersFromQuery() {
  const focus = route.query.focus;
  if (focus === "need_score" || focus === "need_approve_goal") {
    statusFilter.value = [];
  } else {
    const status = route.query.status;
    if (typeof status === "string" && status.trim()) {
      statusFilter.value = status.split(",").map((s) => s.trim()).filter(Boolean) as PerformanceStatus[];
    } else {
      statusFilter.value = [];
    }
  }
  const period = route.query.period;
  periodFilter.value = typeof period === "string" ? period : "";
  const dept = route.query.dept;
  departmentFilter.value = typeof dept === "string" ? dept : "";
  const name = route.query.employeeName;
  employeeNameFilter.value = typeof name === "string" ? name : "";
  const p = route.query.page;
  page.value = typeof p === "string" && /^\d+$/.test(p) ? Math.max(1, parseInt(p, 10)) : 1;
  const ps = route.query.pageSize;
  pageSize.value = typeof ps === "string" && /^\d+$/.test(ps) ? Math.max(1, parseInt(ps, 10)) : 10;
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
  void loadPeriodOptions();
});

watch(
  () => (route.name === "performances" ? JSON.stringify(route.query) : ""),
  () => {
    if (route.name !== "performances") return;
    syncFiltersFromQuery();
    if (skipNextQueryLoad) {
      skipNextQueryLoad = false;
      return;
    }
    void load();
  },
  { immediate: true },
);

watch([page, pageSize], () => {
  if (route.name !== "performances") return;
  replaceListQuery();
});
</script>

<template>
  <div class="ui-page">
    <div v-if="canBatchCreate || canExport || canBatchIssueSelfReview" class="flex flex-wrap justify-end gap-2">
        <button
          v-if="canBatchIssueSelfReview"
          type="button"
          class="ui-btn-outline"
          :disabled="batchIssuing || !selectedIds.length"
          @click="batchIssue"
        >
          {{ batchIssuing ? "下发中…" : `批量下发员工自评${selectedIds.length ? `（${selectedIds.length}）` : ""}` }}
        </button>
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
      <template v-else>
        <!-- mobile card list -->
        <div class="ui-mobile-cards p-3">
          <div v-for="r in items" :key="r.id" class="ui-mobile-card">
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <UserDisplay
                  size="small"
                  :value="{ user_id: r.employeeId, name: r.employeeName || r.employeeId }"
                  class="font-medium"
                />
                <p class="mt-1 text-xs text-muted-foreground">{{ periodLabel(r.period) }}</p>
              </div>
              <PerformanceStatusBadge :status="r.status" />
            </div>
            <div class="mt-2 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
              <span v-if="r.totalScore != null">总分 <strong class="text-foreground">{{ formatScore(r.totalScore) }}</strong></span>
              <span v-if="r.scoreGrade">等级 <strong class="text-foreground">{{ r.scoreGrade }}</strong></span>
            </div>
            <div class="mt-3 flex flex-wrap items-center gap-2">
              <button type="button" class="ui-btn-primary ui-btn-sm flex-1" @click="openDetail(r.id)">详情</button>
              <button
                v-if="r.canIssueSelfReview"
                type="button"
                class="ui-btn-outline ui-btn-sm"
                :disabled="rowIssuingId === r.id"
                @click="issueOne(r)"
              >
                {{ rowIssuingId === r.id ? "下发中…" : "下发自评" }}
              </button>
            </div>
          </div>
        </div>
        <!-- desktop table -->
        <div class="ui-desktop-table">
          <div class="ui-table-wrap">
            <table class="ui-table min-w-[900px]">
              <thead>
                <tr>
                  <th v-if="canBatchIssueSelfReview" class="w-10">
                    <input
                      v-model="allSelectableChecked"
                      type="checkbox"
                      class="h-4 w-4"
                      :disabled="!selectableOnPage.length"
                      aria-label="全选本页可下发"
                    />
                  </th>
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
                  <td v-if="canBatchIssueSelfReview">
                    <input
                      v-if="r.canIssueSelfReview"
                      type="checkbox"
                      class="h-4 w-4"
                      :checked="selectedIds.includes(r.id)"
                      :aria-label="`选择 ${r.employeeName}`"
                      @change="toggleRowSelected(r.id, ($event.target as HTMLInputElement).checked)"
                    />
                  </td>
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
                      <button type="button" class="ui-btn-ghost ui-btn-sm" @click="openDetail(r.id)">
                        详情
                      </button>
                      <button
                        v-if="r.canIssueSelfReview"
                        type="button"
                        class="ui-btn-ghost ui-btn-sm"
                        :disabled="rowIssuingId === r.id"
                        @click="issueOne(r)"
                      >
                        {{ rowIssuingId === r.id ? "下发中…" : "下发自评" }}
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
        </div>
      </template>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </section>
    <CreatePerformanceDialog v-model:open="createOpen" @success="load" />
  </div>
</template>
