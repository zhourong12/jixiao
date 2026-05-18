<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink } from "vue-router";
import type {
  AwardTypeItem,
  EmployeeDirectoryListItem,
  EvaluationPeriodItem,
  EvaluationPeriodType,
  PeriodAwardItem,
  PerformanceLeaderboardItem,
  QuarterLeaderboardDetailResponse,
} from "@/types/api.interface";
import {
  createEvaluationPeriod,
  createPeriodAward,
  deleteEvaluationPeriod,
  deletePeriodAward,
  getAwardTypes,
  getEvaluationLeaderboard,
  getEvaluationLeaderboardQuarterDetail,
  getEvaluationPeriods,
  getPerformancePeriods,
  getPeriodAwards,
} from "@/api/evaluation";
import { getAllEmployees } from "@/api/employees";
import { listPerformances } from "@/api/performances";
import { evaluationPeriodStatusLabel } from "@/constants/evaluationPeriodStatus";
import { formatPeriodDisplay, periodOptionLabel } from "@/utils/period";

const tab = ref<"periods" | "leaderboard" | "awards">("periods");
const periodType = ref<EvaluationPeriodType>("month");
const periods = ref<EvaluationPeriodItem[]>([]);
const loading = ref(false);
const message = ref<string | null>(null);

const leaderboardScope = ref<"month" | "quarter">("month");
const leaderboardKey = ref("");
const leaderboardPeriods = ref<EvaluationPeriodItem[]>([]);
const leaderboardMonthPeriods = ref<EvaluationPeriodItem[]>([]);
const leaderboard = ref<PerformanceLeaderboardItem[]>([]);
const quarterDetailOpen = ref(false);
const quarterDetailLoading = ref(false);
const quarterDetailError = ref<string | null>(null);
const quarterDetail = ref<QuarterLeaderboardDetailResponse | null>(null);
const awardTypes = ref<AwardTypeItem[]>([]);
const awardPeriodScope = ref<"month" | "quarter">("month");
const awardPeriodList = ref<EvaluationPeriodItem[]>([]);
const awardPeriodId = ref("");
const awards = ref<PeriodAwardItem[]>([]);
const awardCreateOpen = ref(false);
/** 从排行榜行打开弹窗时预填周期与员工，仅选奖项即可保存 */
const awardCreatePreset = ref<{
  periodLabel: string;
  periodId: string;
  periodScope: "month" | "quarter";
  employeeId: string;
  employeeName: string;
  recordId?: string | null;
} | null>(null);
const awardDialogError = ref<string | null>(null);
type AwardDialogErrorField = "award" | "employee" | "general";
const awardDialogErrorField = ref<AwardDialogErrorField | null>(null);

function clearAwardDialogError() {
  awardDialogError.value = null;
  awardDialogErrorField.value = null;
}

function setAwardDialogError(field: AwardDialogErrorField, msg: string) {
  awardDialogErrorField.value = field;
  awardDialogError.value = msg;
}
const awardFormAwardCode = ref("");
const awardFormEmployeeId = ref("");
const awardFormRemark = ref("");
const awardEmployeeQuery = ref("");
const awardDirectoryEmployees = ref<EmployeeDirectoryListItem[]>([]);

const dialogOpen = ref(false);
const periodDialogError = ref<string | null>(null);
const formKey = ref("");
const formYear = ref("");
const formMonth = ref("");
const formName = ref("");
const formParentPeriodId = ref("");
const quarterOptions = ref<EvaluationPeriodItem[]>([]);

const yearOptions = computed(() => {
  const y = new Date().getFullYear();
  return Array.from({ length: 11 }, (_, i) => String(y - 3 + i));
});

const monthOptions = computed(() =>
  Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, "0")),
);

const selectableAwardTypes = computed(() =>
  awardTypes.value.filter((t) =>
    awardPeriodScope.value === "month"
      ? t.scope === "month" || t.scope === "both"
      : t.scope === "quarter" || t.scope === "both",
  ),
);

const filteredAwardEmployees = computed(() => {
  const q = awardEmployeeQuery.value.trim().toLowerCase();
  const base = awardDirectoryEmployees.value;
  if (!q) return base;
  return base.filter(
    (e) =>
      e.name.toLowerCase().includes(q) || (e.departmentName ?? "").toLowerCase().includes(q),
  );
});

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

async function loadAwardPeriodList() {
  try {
    const res = await getEvaluationPeriods(awardPeriodScope.value);
    awardPeriodList.value = res.items;
    if (awardPeriodId.value && !res.items.some((x) => x.id === awardPeriodId.value)) {
      awardPeriodId.value = "";
    }
  } catch {
    awardPeriodList.value = [];
  }
}

async function loadLeaderboardPeriods() {
  try {
    const res = await getEvaluationPeriods(leaderboardScope.value);
    let items = [...res.items];
    if (leaderboardScope.value === "month") {
      try {
        const perf = await getPerformancePeriods();
        const known = new Set(items.map((i) => i.periodKey));
        for (const pk of perf.items) {
          const key = pk.trim();
          if (!key || known.has(key)) continue;
          known.add(key);
          items.push({
            id: `perf-${key}`,
            periodType: "month",
            periodKey: key,
            name: formatPeriodDisplay(key),
            sortOrder: 0,
            status: "open",
            createdAt: "",
            updatedAt: "",
          });
        }
        items.sort((a, b) => b.periodKey.localeCompare(a.periodKey));
      } catch {
        /* 仅用考核周期列表 */
      }
      leaderboardMonthPeriods.value = [];
    } else if (leaderboardScope.value === "quarter") {
      const monthRes = await getEvaluationPeriods("month");
      leaderboardMonthPeriods.value = monthRes.items;
    } else {
      leaderboardMonthPeriods.value = [];
    }
    leaderboardPeriods.value = items;
    if (leaderboardKey.value && !items.some((item) => item.periodKey === leaderboardKey.value)) {
      leaderboardKey.value =
        leaderboardScope.value === "quarter" && items.length > 0 ? items[0].periodKey : "";
    } else if (leaderboardScope.value === "quarter" && !leaderboardKey.value && items.length > 0) {
      leaderboardKey.value = items[0].periodKey;
    }
  } catch {
    leaderboardPeriods.value = [];
    leaderboardMonthPeriods.value = [];
    leaderboardKey.value = "";
  }
}

function leaderboardRowPeriodKey(row: PerformanceLeaderboardItem): string {
  return (row.performancePeriod || "").trim();
}

async function openAwardEntryFromLeaderboard(row: PerformanceLeaderboardItem) {
  const periodKey = leaderboardRowPeriodKey(row);
  if (!periodKey) {
    message.value = "缺少周期信息，无法录入评选";
    return;
  }
  if (!row.employeeId) {
    message.value = "缺少员工信息，无法录入评选";
    return;
  }
  awardPeriodScope.value = leaderboardScope.value;
  message.value = null;
  clearAwardDialogError();
  if (!awardTypes.value.length) {
    await loadAwardTypes();
  }
  await loadAwardPeriodList();
  const hit = awardPeriodList.value.find((p) => p.periodKey === periodKey);
  if (!hit) {
    message.value = `未找到评选周期「${formatPeriodDisplay(periodKey)}」，请先在「考核周期」中创建`;
    return;
  }
  awardPeriodId.value = hit.id;
  awardCreatePreset.value = {
    periodLabel: hit.name?.trim() || formatPeriodDisplay(periodKey),
    periodId: hit.id,
    periodScope: leaderboardScope.value,
    employeeId: row.employeeId,
    employeeName: row.employeeName || row.employeeId,
    recordId: row.recordId ?? null,
  };
  awardFormAwardCode.value = selectableAwardTypes.value[0]?.code ?? "";
  awardFormEmployeeId.value = row.employeeId;
  awardFormRemark.value = "";
  awardEmployeeQuery.value = "";
  awardCreateOpen.value = true;
}

function leaderboardPeriodOptionLabel(row: EvaluationPeriodItem): string {
  const label = row.name?.trim() || formatPeriodDisplay(row.periodKey);
  if (leaderboardScope.value === "quarter") {
    return label;
  }
  return `${label}（${row.periodKey}）`;
}

function leaderboardPeriodCell(row: PerformanceLeaderboardItem): string {
  const p = row.performancePeriod?.trim();
  if (!p) return "—";
  return formatPeriodDisplay(p) || p;
}

async function loadLeaderboard() {
  loading.value = true;
  message.value = null;
  try {
    const res = await getEvaluationLeaderboard({
      scope: leaderboardScope.value,
      key: leaderboardKey.value.trim() || undefined,
    });
    leaderboard.value = res.items;
  } catch (e) {
    leaderboard.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

function formatLeaderboardScore(value?: number | null) {
  if (value == null || Number.isNaN(value)) return "—";
  return String(value);
}

function quarterMonthKeys(quarterKey: string): string[] {
  const match = /^(\d{4})-Q([1-4])$/.exec(quarterKey.trim());
  if (!match) return [];
  const year = Number(match[1]);
  const quarter = Number(match[2]);
  const startMonth = (quarter - 1) * 3 + 1;
  return [0, 1, 2].map((offset) => {
    const month = startMonth + offset;
    return `${year}-${String(month).padStart(2, "0")}`;
  });
}

async function loadQuarterDetailFallback(row: PerformanceLeaderboardItem): Promise<QuarterLeaderboardDetailResponse> {
  const quarterKey = (leaderboardKey.value.trim() || row.performancePeriod || "").trim();
  const monthRes = await getEvaluationPeriods("month");
  const configured = monthRes.items
    .filter((item) => item.parentPeriodKey === quarterKey)
    .sort((a, b) => a.periodKey.localeCompare(b.periodKey));
  const monthPlans = configured.length
    ? configured.map((item) => ({ periodKey: item.periodKey, periodName: item.name || item.periodKey }))
    : quarterMonthKeys(quarterKey).map((key) => ({ periodKey: key, periodName: key }));

  const monthlyItems = await Promise.all(
    monthPlans.map(async (plan) => {
      const res = await listPerformances({
        period: plan.periodKey,
        page: 1,
        pageSize: 20,
        employeeName: row.employeeName,
      });
      const hit = res.items.find((item) => item.employeeId === row.employeeId);
      if (!hit) {
        return { periodKey: plan.periodKey, periodName: plan.periodName };
      }
      return {
        periodKey: plan.periodKey,
        periodName: plan.periodName,
        recordId: hit.id,
        totalScore: hit.totalScore,
        status: hit.status,
      };
    }),
  );

  return {
    quarterKey,
    employeeId: row.employeeId,
    employeeName: row.employeeName,
    quarterRecordId: row.recordId,
    quarterTotalScore: row.totalScore,
    monthlyItems,
  };
}

async function openQuarterDetail(row: PerformanceLeaderboardItem) {
  const quarterKey = (leaderboardKey.value.trim() || row.performancePeriod || "").trim();
  if (!quarterKey || !/^(\d{4})-Q[1-4]$/i.test(quarterKey)) {
    message.value = "请选择季度周期";
    return;
  }
  if (!row.employeeId) {
    message.value = "缺少员工信息，无法查看详情";
    return;
  }
  quarterDetailOpen.value = true;
  quarterDetailLoading.value = true;
  quarterDetailError.value = null;
  quarterDetail.value = null;
  try {
    quarterDetail.value = await getEvaluationLeaderboardQuarterDetail({
      key: quarterKey,
      employeeId: row.employeeId,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : "加载详情失败";
    if (/not found|404/i.test(msg)) {
      try {
        quarterDetail.value = await loadQuarterDetailFallback(row);
        return;
      } catch {
        quarterDetailError.value = msg;
        return;
      }
    }
    quarterDetailError.value = msg;
  } finally {
    quarterDetailLoading.value = false;
  }
}

function closeQuarterDetail() {
  quarterDetailOpen.value = false;
  quarterDetailError.value = null;
  quarterDetail.value = null;
}

async function loadQuarterOptions() {
  try {
    const res = await getEvaluationPeriods("quarter");
    quarterOptions.value = res.items;
  } catch {
    quarterOptions.value = [];
  }
}

async function savePeriod() {
  periodDialogError.value = null;
  let periodKey = "";
  if (periodType.value === "month") {
    if (!formYear.value.trim() || !formMonth.value.trim()) {
      periodDialogError.value = "请选择考核年份与月份";
      return;
    }
    periodKey = `${formYear.value.trim()}-${formMonth.value.trim()}`;
  } else {
    periodKey = formKey.value.trim();
  }
  if (!periodKey) {
    periodDialogError.value = "请填写周期 key";
    return;
  }
  if (periodType.value === "month" && !formParentPeriodId.value.trim()) {
    periodDialogError.value = "月度周期请选择归属季度";
    return;
  }
  try {
    await createEvaluationPeriod({
      periodType: periodType.value,
      periodKey,
      name: formName.value.trim() || periodKey,
      ...(periodType.value === "month"
        ? { parentPeriodId: formParentPeriodId.value.trim() }
        : {}),
    });
    dialogOpen.value = false;
    formKey.value = "";
    formYear.value = "";
    formMonth.value = "";
    formName.value = "";
    formParentPeriodId.value = "";
    await loadPeriods();
  } catch (e) {
    periodDialogError.value = e instanceof Error ? e.message : "创建失败";
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

async function loadAwards() {
  loading.value = true;
  try {
    const res = await getPeriodAwards({
      periodId: awardPeriodId.value || undefined,
      periodType: awardPeriodScope.value,
    });
    awards.value = res.items;
  } catch (e) {
    awards.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadAwardTypes() {
  try {
    const res = await getAwardTypes();
    awardTypes.value = res.items;
  } catch {
    awardTypes.value = [];
  }
}

async function removeAward(row: PeriodAwardItem) {
  if (!window.confirm(`删除奖项「${row.awardName}」？`)) return;
  try {
    await deletePeriodAward(row.id);
    await loadAwards();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

async function openAwardCreateDialog() {
  if (!awardPeriodId.value) {
    message.value = "请先选择评选周期";
    return;
  }
  awardCreatePreset.value = null;
  awardFormAwardCode.value = selectableAwardTypes.value[0]?.code ?? "";
  awardFormEmployeeId.value = "";
  awardFormRemark.value = "";
  awardEmployeeQuery.value = "";
  clearAwardDialogError();
  awardCreateOpen.value = true;
  try {
    const res = await getAllEmployees();
    awardDirectoryEmployees.value = res.items ?? [];
  } catch {
    awardDirectoryEmployees.value = [];
  }
}

function closeAwardCreateDialog() {
  awardCreateOpen.value = false;
  awardCreatePreset.value = null;
  clearAwardDialogError();
}

function awardPeriodCellLabel(row: PeriodAwardItem): string {
  return periodOptionLabel(row.periodKey ?? "", row.periodName);
}

async function submitNewAward() {
  clearAwardDialogError();
  const periodId = awardPeriodId.value || awardCreatePreset.value?.periodId || "";
  if (!periodId) {
    message.value = "请选择评选周期";
    return;
  }
  if (!awardFormAwardCode.value.trim()) {
    setAwardDialogError("award", "请选择奖项");
    return;
  }
  if (!awardFormEmployeeId.value.trim()) {
    setAwardDialogError("employee", "请选择员工");
    return;
  }
  loading.value = true;
  try {
    const created = await createPeriodAward({
      periodId,
      awardCode: awardFormAwardCode.value.trim(),
      employeeId: awardFormEmployeeId.value.trim(),
      performanceRecordId: awardCreatePreset.value?.recordId || undefined,
      remark: awardFormRemark.value.trim() || undefined,
    });
    const savedScope = awardCreatePreset.value?.periodScope ?? awardPeriodScope.value;
    const savedPeriodId = created.periodId || periodId;
    closeAwardCreateDialog();
    message.value = null;
    tab.value = "awards";
    awardPeriodScope.value = savedScope;
    awardPeriodId.value = savedPeriodId;
    await loadAwardPeriodList();
    await loadAwards();
  } catch (e) {
    setAwardDialogError("general", e instanceof Error ? e.message : "录入失败");
  } finally {
    loading.value = false;
  }
}

function openCreatePeriodDialog() {
  formKey.value = "";
  formName.value = "";
  formParentPeriodId.value = "";
  periodDialogError.value = null;
  const d = new Date();
  formYear.value = String(d.getFullYear());
  formMonth.value = String(d.getMonth() + 1).padStart(2, "0");
  dialogOpen.value = true;
  if (periodType.value === "month") {
    void loadQuarterOptions();
  }
}

onMounted(() => {
  void loadPeriods();
  void loadAwardTypes();
});

watch(leaderboardScope, () => {
  leaderboardKey.value = "";
  void loadLeaderboardPeriods().then(() => {
    if (leaderboardScope.value === "quarter" && leaderboardPeriods.value.length && !leaderboardKey.value) {
      leaderboardKey.value = leaderboardPeriods.value[0].periodKey;
    }
    if (tab.value === "leaderboard") void loadLeaderboard();
  });
});

watch(tab, (value) => {
  if (value === "leaderboard") {
    void loadLeaderboardPeriods().then(() => loadLeaderboard());
  }
  if (value === "awards") {
    void loadAwardPeriodList().then(() => loadAwards());
  }
});

watch(awardPeriodScope, () => {
  awardPeriodId.value = "";
  void loadAwardPeriodList().then(() => {
    if (tab.value === "awards") void loadAwards();
  });
});

watch(awardPeriodId, () => {
  if (tab.value === "awards") void loadAwards();
});

watch([dialogOpen, periodType], ([open]) => {
  if (open && periodType.value === "month") {
    void loadQuarterOptions();
  }
});
</script>

<template>
  <div class="space-y-6">
    <p v-if="message && !awardCreateOpen && !dialogOpen" class="text-sm text-destructive">{{ message }}</p>
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
      <button
        type="button"
        class="border-b-2 px-3 py-2 text-sm"
        :class="tab === 'awards' ? 'border-primary text-primary' : 'border-transparent'"
        @click="tab = 'awards'"
      >
        评选录入
      </button>
    </div>

    <template v-if="tab === 'periods'">
      <div class="flex flex-wrap items-center gap-3">
        <select v-model="periodType" class="rounded-md border px-3 py-2 text-sm" @change="loadPeriods">
          <option value="month">月度</option>
          <option value="quarter">季度</option>
        </select>
        <button
          type="button"
          class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground"
          @click="openCreatePeriodDialog"
        >
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
              <th v-if="periodType === 'month'" class="px-3 py-2">归属季度</th>
              <th class="px-3 py-2">状态</th>
              <th class="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in periods" :key="row.id" class="border-b hover:bg-accent/40">
              <td class="px-3 py-2 font-mono text-xs">{{ row.periodKey }}</td>
              <td class="px-3 py-2">{{ row.name }}</td>
              <td v-if="periodType === 'month'" class="px-3 py-2 font-mono text-xs">
                {{ row.parentPeriodKey || "—" }}
              </td>
              <td class="px-3 py-2">{{ evaluationPeriodStatusLabel(row.status) }}</td>
              <td class="px-3 py-2">
                <button type="button" class="text-destructive hover:underline" @click="removePeriod(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else-if="tab === 'leaderboard'">
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="mb-1 block text-sm">范围</label>
          <select v-model="leaderboardScope" class="rounded-md border px-3 py-2 text-sm">
            <option value="month">月</option>
            <option value="quarter">季</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm">周期</label>
          <select v-model="leaderboardKey" class="min-w-[12rem] rounded-md border px-3 py-2 text-sm">
            <option v-if="leaderboardScope === 'month'" value="">全部</option>
            <option v-for="row in leaderboardPeriods" :key="row.id" :value="row.periodKey">
              {{ leaderboardPeriodOptionLabel(row) }}
            </option>
          </select>
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
              <th class="px-3 py-2">周期</th>
              <th class="px-3 py-2">总分</th>
              <th class="px-3 py-2">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in leaderboard"
              :key="`${row.rank}-${row.employeeId}-${row.performancePeriod}`"
              class="border-b"
            >
              <td class="px-3 py-2">{{ row.rank }}</td>
              <td class="px-3 py-2">{{ row.employeeName }}</td>
              <td class="px-3 py-2">{{ row.departmentName || "—" }}</td>
              <td class="px-3 py-2">{{ leaderboardPeriodCell(row) }}</td>
              <td class="px-3 py-2">{{ formatLeaderboardScore(row.totalScore) }}</td>
              <td class="px-3 py-2 space-x-2 whitespace-nowrap">
                <button
                  v-if="leaderboardScope === 'quarter' && /^(\d{4})-Q[1-4]$/i.test(row.performancePeriod || '')"
                  type="button"
                  class="text-primary hover:underline"
                  @click="openQuarterDetail(row)"
                >
                  详情
                </button>
                <button
                  v-if="leaderboardRowPeriodKey(row)"
                  type="button"
                  class="text-primary hover:underline"
                  @click="openAwardEntryFromLeaderboard(row)"
                >
                  评选录入
                </button>
                <span
                  v-if="
                    leaderboardScope === 'quarter' &&
                    !/^(\d{4})-Q[1-4]$/i.test(row.performancePeriod || '') &&
                    !leaderboardRowPeriodKey(row)
                  "
                  class="text-muted-foreground"
                >
                  —
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <div class="flex flex-wrap items-end gap-3">
        <div>
          <label class="mb-1 block text-sm">周期类型</label>
          <select v-model="awardPeriodScope" class="rounded-md border border-border bg-card px-3 py-2 text-sm">
            <option value="month">月度</option>
            <option value="quarter">季度</option>
          </select>
        </div>
        <div>
          <label class="mb-1 block text-sm">评选周期</label>
          <select
            v-model="awardPeriodId"
            class="min-w-[14rem] rounded-md border border-border bg-card px-3 py-2 text-sm"
          >
            <option value="">全部</option>
            <option v-for="row in awardPeriodList" :key="row.id" :value="row.id">
              {{ periodOptionLabel(row.periodKey, row.name) }}
            </option>
          </select>
        </div>
        <button type="button" class="rounded-md border border-border px-3 py-2 text-sm" @click="loadAwardPeriodList">
          刷新周期
        </button>
        <button
          type="button"
          class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground"
          @click="openAwardCreateDialog"
        >
          新增评选
        </button>
      </div>
      <p class="text-xs text-muted-foreground">
        可选奖项（当前周期类型）：{{ selectableAwardTypes.map((item) => item.name).join("、") || "暂无" }}
      </p>
      <div v-if="loading" class="py-12 text-center text-muted-foreground">加载中…</div>
      <div v-else class="overflow-x-auto rounded-md border border-border">
        <table class="w-full min-w-[640px] text-left text-sm">
          <thead class="border-b bg-muted/40">
            <tr>
              <th class="px-3 py-2">评选周期</th>
              <th class="px-3 py-2">奖项</th>
              <th class="px-3 py-2">员工</th>
              <th class="px-3 py-2">备注</th>
              <th class="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in awards" :key="row.id" class="border-b">
              <td class="px-3 py-2">{{ awardPeriodCellLabel(row) }}</td>
              <td class="px-3 py-2">{{ row.awardName }}</td>
              <td class="px-3 py-2">{{ row.employeeName || row.employeeId }}</td>
              <td class="px-3 py-2">{{ row.remark || "—" }}</td>
              <td class="px-3 py-2">
                <button type="button" class="text-destructive hover:underline" @click="removeAward(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <Teleport to="body">
      <div
        v-if="awardCreateOpen"
        class="fixed inset-0 z-[90] flex items-center justify-center bg-black/40 p-4"
        @click.self="closeAwardCreateDialog"
      >
        <div class="w-full max-w-md rounded-md border border-border bg-card p-6 shadow-lg" @click.stop>
          <h2 class="text-lg font-semibold">新增评选录入</h2>
          <div class="mt-4 space-y-3">
            <template v-if="awardCreatePreset">
              <div>
                <label class="mb-1 block text-sm font-medium text-muted-foreground">评选周期</label>
                <p class="text-sm font-medium">{{ awardCreatePreset.periodLabel }}</p>
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium text-muted-foreground">员工</label>
                <p class="text-sm font-medium">{{ awardCreatePreset.employeeName }}</p>
              </div>
            </template>
            <div>
              <label class="mb-1 block text-sm font-medium">奖项</label>
              <select v-model="awardFormAwardCode" class="w-full rounded-md border border-border bg-card px-3 py-2 text-sm">
                <option v-for="t in selectableAwardTypes" :key="t.code" :value="t.code">{{ t.name }}</option>
              </select>
              <p v-if="awardDialogErrorField === 'award' && awardDialogError" class="mt-1 text-xs text-destructive">{{ awardDialogError }}</p>
            </div>
            <div v-if="!awardCreatePreset">
              <label class="mb-1 block text-sm font-medium">员工</label>
              <input
                v-model="awardEmployeeQuery"
                type="search"
                class="mb-2 w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
                placeholder="搜索姓名或部门"
              />
              <select v-model="awardFormEmployeeId" class="w-full rounded-md border border-border bg-card px-3 py-2 text-sm">
                <option value="">请选择员工</option>
                <option v-for="e in filteredAwardEmployees" :key="e.userId" :value="e.userId">
                  {{ e.name }}<template v-if="e.departmentName">（{{ e.departmentName }}）</template>
                </option>
              </select>
              <p v-if="awardDialogErrorField === 'employee' && awardDialogError" class="mt-1 text-xs text-destructive">{{ awardDialogError }}</p>
            </div>
            <div>
              <label class="mb-1 block text-sm font-medium">备注（可选）</label>
              <input v-model="awardFormRemark" class="w-full rounded-md border border-border bg-card px-3 py-2 text-sm" />
            </div>
          </div>
          <p v-if="awardDialogErrorField === 'general' && awardDialogError" class="mt-3 text-sm text-destructive">{{ awardDialogError }}</p>
          <div class="mt-6 flex justify-end gap-2">
            <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="closeAwardCreateDialog">
              取消
            </button>
            <button
              type="button"
              class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
              :disabled="loading"
              @click="submitNewAward"
            >
              保存
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="quarterDetailOpen"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4"
        @click.self="closeQuarterDetail"
      >
        <div class="w-full max-w-2xl rounded-md border bg-card p-6 shadow-lg" @click.stop>
          <div class="flex items-start justify-between gap-4">
            <div>
              <h2 class="text-lg font-semibold">季度排行详情</h2>
              <p v-if="quarterDetail" class="mt-1 text-sm text-muted-foreground">
                {{ quarterDetail.employeeName }} · {{ quarterDetail.quarterKey }}
              </p>
            </div>
            <button type="button" class="rounded-md border px-3 py-1.5 text-sm" @click="closeQuarterDetail">关闭</button>
          </div>
          <div v-if="quarterDetailLoading" class="py-10 text-center text-sm text-muted-foreground">加载中…</div>
          <p v-else-if="quarterDetailError" class="mt-4 text-sm text-destructive">{{ quarterDetailError }}</p>
          <template v-else-if="quarterDetail">
          <div class="mt-4 grid grid-cols-2 gap-4 rounded-md border border-border bg-muted/20 p-4 text-sm md:grid-cols-3">
            <div>
              <p class="text-muted-foreground">季度总分</p>
              <p class="mt-1 text-lg font-semibold">{{ formatLeaderboardScore(quarterDetail.quarterTotalScore) }}</p>
            </div>
            <div class="md:col-span-2">
              <p class="text-muted-foreground">季度绩效</p>
              <p class="mt-1">
                <RouterLink
                  v-if="quarterDetail.quarterRecordId"
                  :to="`/performances/${quarterDetail.quarterRecordId}`"
                  class="text-primary hover:underline"
                  @click="closeQuarterDetail"
                >
                  查看季度绩效详情
                </RouterLink>
                <span v-else class="text-muted-foreground">暂无季度绩效记录</span>
              </p>
            </div>
          </div>
          <div class="mt-4 overflow-x-auto rounded-md border border-border">
            <table class="w-full min-w-[520px] text-left text-sm">
              <thead class="border-b bg-muted/40">
                <tr>
                  <th class="px-3 py-2">月度</th>
                  <th class="px-3 py-2">总分</th>
                  <th class="px-3 py-2">状态</th>
                  <th class="px-3 py-2">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in quarterDetail.monthlyItems" :key="item.periodKey" class="border-b">
                  <td class="px-3 py-2">{{ item.periodName || item.periodKey }}</td>
                  <td class="px-3 py-2">{{ formatLeaderboardScore(item.totalScore) }}</td>
                  <td class="px-3 py-2">{{ item.status || "—" }}</td>
                  <td class="px-3 py-2">
                    <RouterLink
                      v-if="item.recordId"
                      :to="`/performances/${item.recordId}`"
                      class="text-primary hover:underline"
                      @click="closeQuarterDetail"
                    >
                      查看绩效
                    </RouterLink>
                    <span v-else class="text-muted-foreground">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
        </div>
      </div>
    </Teleport>

    <div v-if="dialogOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="dialogOpen = false">
      <div class="w-full max-w-md rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">新建周期</h2>
        <p class="mt-1 text-sm text-muted-foreground">
          当前类型：<span class="font-medium text-foreground">{{ periodType === "month" ? "月度（须绑定归属季度）" : "季度" }}</span>
        </p>
        <div class="mt-4 space-y-3">
          <template v-if="periodType === 'month'">
            <div class="flex flex-wrap gap-2">
              <div class="min-w-[7rem] flex-1">
                <label class="mb-1 block text-sm font-medium">年份</label>
                <select v-model="formYear" class="w-full rounded-md border px-3 py-2 text-sm">
                  <option v-for="y in yearOptions" :key="y" :value="y">{{ y }} 年</option>
                </select>
              </div>
              <div class="min-w-[7rem] flex-1">
                <label class="mb-1 block text-sm font-medium">月份</label>
                <select v-model="formMonth" class="w-full rounded-md border px-3 py-2 text-sm">
                  <option v-for="m in monthOptions" :key="m" :value="m">{{ m }} 月</option>
                </select>
              </div>
            </div>
            <p class="text-xs text-muted-foreground">周期 Key 将保存为 {{ formYear }}-{{ formMonth }}（须与归属季度的三个月一致）。</p>
          </template>
          <input
            v-else
            v-model="formKey"
            class="w-full rounded-md border px-3 py-2 text-sm"
            placeholder="periodKey，如 2026-Q2"
          />
          <input v-model="formName" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="显示名称（可选）" />
          <template v-if="periodType === 'month'">
            <label class="mb-1 block text-sm font-medium">归属季度</label>
            <select v-model="formParentPeriodId" class="w-full rounded-md border px-3 py-2 text-sm">
              <option value="">请选择季度周期</option>
              <option v-for="q in quarterOptions" :key="q.id" :value="q.id">{{ q.name }}（{{ q.periodKey }}）</option>
            </select>
            <p class="text-xs text-muted-foreground">月度 key 必须落在所选季度的三个月内（如 2026-05 对应 2026-Q2）。</p>
          </template>
        </div>
        <p v-if="periodDialogError" class="mt-3 text-sm text-destructive">{{ periodDialogError }}</p>
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
