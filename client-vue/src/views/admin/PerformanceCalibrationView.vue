<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { PerformanceListItem } from "@/types/api.interface";
import { getSupervisorCalibrationQueue } from "@/api/performances";
import { PERFORMANCE_STATUS_LABELS } from "@/constants/performanceStatus";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const router = useRouter();
const allowed = computed(() => session.allow("admin_performance_calibration") && session.role === "super_admin");

const loading = ref(false);
const items = ref<PerformanceListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const periodFilter = ref("");
const departmentId = ref("");
const employeeNameFilter = ref("");

async function load() {
  if (!allowed.value) return;
  loading.value = true;
  try {
    const res = await getSupervisorCalibrationQueue({
      page: page.value,
      pageSize,
      period: periodFilter.value || undefined,
      departmentId: departmentId.value || undefined,
      employeeName: employeeNameFilter.value || undefined,
    });
    items.value = res.items;
    total.value = res.total;
  } catch {
    items.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  void load();
}

function reset() {
  periodFilter.value = "";
  departmentId.value = "";
  employeeNameFilter.value = "";
  page.value = 1;
  void load();
}

onMounted(() => void load());
watch([page, allowed], () => void load());
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-bold">绩效校准</h1>
    <p v-if="!allowed" class="rounded-md border border-border bg-card p-4 text-sm text-muted-foreground">
      仅超级管理员可访问上级评分校准队列�?    </p>
    <template v-else>
      <div class="flex flex-wrap items-end gap-3 rounded-md border border-border bg-card p-4 shadow-sm">
        <div>
          <label class="mb-1 block text-sm">周期</label>
          <input v-model="periodFilter" class="w-36 rounded-md border px-3 py-2 text-sm" placeholder="�?2026-Q1" />
        </div>
        <div>
          <label class="mb-1 block text-sm">部门 ID</label>
          <input v-model="departmentId" class="w-40 rounded-md border px-3 py-2 text-sm" />
        </div>
        <div>
          <label class="mb-1 block text-sm">员工姓名</label>
          <input v-model="employeeNameFilter" class="w-40 rounded-md border px-3 py-2 text-sm" @keyup.enter="search" />
        </div>
        <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="search">
          查询
        </button>
        <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="reset">重置</button>
      </div>
      <p class="text-sm text-muted-foreground">�?{{ total }} �?/p>
      <div v-if="loading" class="py-16 text-center text-muted-foreground">加载中�?/div>
      <div v-else class="overflow-x-auto rounded-md border border-border">
        <table class="w-full min-w-[720px] text-left text-sm">
          <thead class="border-b bg-muted/40">
            <tr>
              <th class="px-3 py-2">员工</th>
              <th class="px-3 py-2">周期</th>
              <th class="px-3 py-2">状�?/th>
              <th class="px-3 py-2">上级</th>
              <th class="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in items" :key="r.id" class="border-b hover:bg-accent/40">
              <td class="px-3 py-2">{{ r.employeeName || r.employeeId }}</td>
              <td class="px-3 py-2">{{ r.period }}</td>
              <td class="px-3 py-2">{{ PERFORMANCE_STATUS_LABELS[r.status] || r.status }}</td>
              <td class="px-3 py-2">{{ r.managerName || r.managerId }}</td>
              <td class="px-3 py-2">
                <button type="button" class="text-primary hover:underline" @click="router.push(`/performances/${r.id}`)">
                  详情
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
