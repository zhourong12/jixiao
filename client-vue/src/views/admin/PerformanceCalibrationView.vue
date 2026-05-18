<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { PerformanceListItem } from "@/types/api.interface";
import { getSupervisorCalibrationQueue } from "@/api/performances";
import DepartmentTreeSelect from "@/components/business-ui/DepartmentTreeSelect.vue";
import { parseDepartmentFilter } from "@/utils/departmentFilter";
import SearchableSelect from "@/components/ui/SearchableSelect.vue";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import PerformanceStatusBadge from "@/components/ui/PerformanceStatusBadge.vue";
import ListPagination from "@/components/ui/ListPagination.vue";
import { useSessionStore } from "@/stores/session";
import { usePerformanceMonthPeriodOptions } from "@/composables/usePerformanceMonthPeriodOptions";
import { formatDateTime } from "@/utils/datetime";
import { formatPeriodDisplay } from "@/utils/period";

const session = useSessionStore();
const { periodSelectOptions, periodLoading, loadPeriodOptions } = usePerformanceMonthPeriodOptions();
const router = useRouter();
const sessionReady = computed(() => session.loaded && !session.permLoading);
const allowed = computed(
  () => sessionReady.value && session.allow("admin_performance_calibration") && session.role === "super_admin",
);

const loading = ref(false);
const items = ref<PerformanceListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const periodFilter = ref("");
const departmentFilter = ref("");
const employeeNameFilter = ref("");
const message = ref<string | null>(null);
let loadSeq = 0;

async function load() {
  if (!allowed.value) {
    loading.value = false;
    items.value = [];
    total.value = 0;
    return;
  }
  const seq = ++loadSeq;
  loading.value = true;
  message.value = null;
  try {
    const res = await getSupervisorCalibrationQueue({
      page: page.value,
      pageSize: pageSize.value,
      period: periodFilter.value.trim() || undefined,
      ...parseDepartmentFilter(departmentFilter.value),
      employeeName: employeeNameFilter.value.trim() || undefined,
    });
    if (seq !== loadSeq) return;
    items.value = res.items;
    total.value = res.total;
  } catch (e) {
    if (seq !== loadSeq) return;
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    if (seq === loadSeq) loading.value = false;
  }
}

function search() {
  if (page.value !== 1) {
    page.value = 1;
    return;
  }
  void load();
}

function reset() {
  periodFilter.value = "";
  departmentFilter.value = "";
  employeeNameFilter.value = "";
  if (page.value !== 1) {
    page.value = 1;
    return;
  }
  void load();
}

function periodLabel(period?: string): string {
  return formatPeriodDisplay(period);
}

watch(
  allowed,
  (ok) => {
    if (ok) {
      void loadPeriodOptions();
      void load();
    } else {
      loading.value = false;
      items.value = [];
      total.value = 0;
    }
  },
  { immediate: true },
);
watch([page, pageSize], () => {
  if (allowed.value) void load();
});
</script>

<template>
  <div class="ui-page">
    <p v-if="!sessionReady" class="ui-loading">加载中...</p>
    <p v-else-if="!allowed" class="ui-alert-info">仅超级管理员可访问上级评分校准队列。</p>
    <template v-else>
      <p class="text-sm text-muted-foreground">待校准队列，仅展示流程状态为「待校准」的绩效记录。</p>
      <p v-if="message" class="ui-alert-danger">{{ message }}</p>
      <section class="ui-card">
        <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <div>
            <label class="ui-label">周期</label>
            <SearchableSelect
              v-model="periodFilter"
              :options="periodSelectOptions"
              :loading="periodLoading"
              placeholder="全部"
              empty-text="没有匹配的周期"
              @change="search"
            />
          </div>
          <div>
            <label class="ui-label">部门</label>
            <DepartmentTreeSelect v-model="departmentFilter" @change="search" />
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
              <th>直属上级</th>
              <th>虚线上级</th>
              <th>更新时间</th>
              <th class="w-28">操作</th>
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
              <td>
                <UserDisplay
                  v-if="r.dottedManagerId"
                  size="small"
                  :value="{ user_id: r.dottedManagerId, name: r.dottedManagerName || r.dottedManagerId }"
                />
                <span v-else class="text-muted-foreground">-</span>
              </td>
              <td class="text-muted-foreground">{{ formatDateTime(r.updatedAt) }}</td>
              <td>
                <button type="button" class="ui-btn-ghost ui-btn-sm" @click="router.push(`/performances/${r.id}`)">
                  详情
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        </div>
        <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </section>
    </template>
  </div>
</template>
