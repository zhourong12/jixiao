<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { EvaluationPeriodItem, EvaluationPeriodType, PerformanceLeaderboardItem } from "@/types/api.interface";
import {
  createEvaluationPeriod,
  deleteEvaluationPeriod,
  getEvaluationLeaderboard,
  getEvaluationPeriods,
} from "@/api/evaluation";

const tab = ref<"periods" | "leaderboard">("periods");
const periodType = ref<EvaluationPeriodType>("month");
const periods = ref<EvaluationPeriodItem[]>([]);
const loading = ref(false);
const message = ref<string | null>(null);

const leaderboardScope = ref<"month" | "quarter">("month");
const leaderboardKey = ref("");
const leaderboard = ref<PerformanceLeaderboardItem[]>([]);

const dialogOpen = ref(false);
const formKey = ref("");
const formName = ref("");

async function loadPeriods() {
  loading.value = true;
  try {
    const res = await getEvaluationPeriods(periodType.value);
    periods.value = res.items;
  } catch (e) {
    periods.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadLeaderboard() {
  if (!leaderboardKey.value.trim()) return;
  loading.value = true;
  try {
    const res = await getEvaluationLeaderboard({
      scope: leaderboardScope.value,
      key: leaderboardKey.value.trim(),
    });
    leaderboard.value = res.items;
  } catch (e) {
    leaderboard.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function savePeriod() {
  if (!formKey.value.trim()) {
    message.value = "请填写周期 key";
    return;
  }
  try {
    await createEvaluationPeriod({
      periodType: periodType.value,
      periodKey: formKey.value.trim(),
      name: formName.value.trim() || formKey.value.trim(),
    });
    dialogOpen.value = false;
    formKey.value = "";
    formName.value = "";
    await loadPeriods();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "创建失败";
  }
}

async function removePeriod(row: EvaluationPeriodItem) {
  if (!window.confirm(`删除周期「${row.name}」？`)) return;
  try {
    await deleteEvaluationPeriod(row.id);
    await loadPeriods();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => void loadPeriods());
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-bold">周期与评选</h1>
    <p v-if="message" class="text-sm text-destructive">{{ message }}</p>
    <div class="flex gap-2 border-b border-border">
      <button
        type="button"
        class="border-b-2 px-3 py-2 text-sm"
        :class="tab === 'periods' ? 'border-primary text-primary' : 'border-transparent'"
        @click="tab = 'periods'"
      >
        考核周期
      </button>
      <button
        type="button"
        class="border-b-2 px-3 py-2 text-sm"
        :class="tab === 'leaderboard' ? 'border-primary text-primary' : 'border-transparent'"
        @click="tab = 'leaderboard'"
      >
        排行榜
      </button>
    </div>

    <template v-if="tab === 'periods'">
      <div class="flex flex-wrap items-center gap-3">
        <select v-model="periodType" class="rounded-md border px-3 py-2 text-sm" @change="loadPeriods">
          <option value="month">月度</option>
          <option value="quarter">季度</option>
        </select>
        <button type="button" class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground" @click="dialogOpen = true">
          新建周期
        </button>
      </div>
      <div v-if="loading" class="py-12 text-center text-muted-foreground">加载中…</div>
      <div v-else class="overflow-x-auto rounded-md border border-border">
        <table class="w-full min-w-[640px] text-left text-sm">
          <thead class="border-b bg-muted/40">
            <tr>
              <th class="px-3 py-2">Key</th>
              <th class="px-3 py-2">名称</th>
              <th class="px-3 py-2">状态</th>
              <th class="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in periods" :key="row.id" class="border-b hover:bg-accent/40">
              <td class="px-3 py-2 font-mono text-xs">{{ row.periodKey }}</td>
              <td class="px-3 py-2">{{ row.name }}</td>
              <td class="px-3 py-2">{{ row.status }}</td>
              <td class="px-3 py-2">
                <button type="button" class="text-destructive hover:underline" @click="removePeriod(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="mb-1 block text-sm">范围</label>
          <select v-model="leaderboardScope" class="rounded-md border px-3 py-2 text-sm">
            <option value="month">月</option>
            <option value="quarter">季</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm">周期 key</label>
          <input v-model="leaderboardKey" class="rounded-md border px-3 py-2 text-sm" placeholder="2026-05 或 2026-Q2" />
        </div>
        <button type="button" class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground" @click="loadLeaderboard">
          查询
        </button>
      </div>
      <div v-if="loading" class="py-12 text-center text-muted-foreground">加载中…</div>
      <div v-else class="overflow-x-auto rounded-md border border-border">
        <table class="w-full min-w-[640px] text-left text-sm">
          <thead class="border-b bg-muted/40">
            <tr>
              <th class="px-3 py-2">排名</th>
              <th class="px-3 py-2">员工</th>
              <th class="px-3 py-2">部门</th>
              <th class="px-3 py-2">总分</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in leaderboard" :key="row.recordId" class="border-b">
              <td class="px-3 py-2">{{ row.rank }}</td>
              <td class="px-3 py-2">{{ row.employeeName }}</td>
              <td class="px-3 py-2">{{ row.departmentName || "—" }}</td>
              <td class="px-3 py-2">{{ row.totalScore }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <div v-if="dialogOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="dialogOpen = false">
      <div class="w-full max-w-md rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">新建周期</h2>
        <div class="mt-4 space-y-3">
          <input v-model="formKey" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="periodKey" />
          <input v-model="formName" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="显示名称" />
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="dialogOpen = false">取消</button>
          <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="savePeriod">
            保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
