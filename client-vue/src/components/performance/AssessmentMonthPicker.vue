<script setup lang="ts">
import { computed, ref, watch } from "vue";

export type AssessmentMonthItem = { periodKey: string; name?: string };

const props = withDefaults(
  defineProps<{
    items: AssessmentMonthItem[];
    disabled?: boolean;
  }>(),
  { items: () => [], disabled: false },
);

const model = defineModel<string>({ default: "" });

const selectedYear = ref("");
const selectedMonthKey = ref("");

const years = computed(() => {
  const ys = new Set<string>();
  for (const it of props.items) {
    const m = /^(\d{4})-\d{2}$/.exec(String(it.periodKey ?? "").trim());
    if (m) ys.add(m[1]);
  }
  return Array.from(ys).sort((a, b) => b.localeCompare(a));
});

const monthsInYear = computed(() => {
  const y = selectedYear.value.trim();
  if (!y) return [] as AssessmentMonthItem[];
  const out: AssessmentMonthItem[] = [];
  for (const it of props.items) {
    const pk = String(it.periodKey ?? "").trim();
    const m = /^(\d{4})-(\d{2})$/.exec(pk);
    if (!m || m[1] !== y) continue;
    out.push({ periodKey: pk, name: it.name });
  }
  return out.sort((a, b) => a.periodKey.localeCompare(b.periodKey));
});

function ensureYearInList() {
  if (selectedYear.value && years.value.includes(selectedYear.value)) return;
  selectedYear.value = years.value[0] ?? "";
}

function ensureMonthInYear() {
  const months = monthsInYear.value;
  if (selectedMonthKey.value && months.some((it) => it.periodKey === selectedMonthKey.value)) return;
  selectedMonthKey.value = "";
}

function emitModelFromMonth() {
  const pk = selectedMonthKey.value.trim();
  if (pk && props.items.some((i) => i.periodKey === pk)) {
    if (model.value !== pk) model.value = pk;
    return;
  }
  if (model.value !== "") model.value = "";
}

watch(
  () => model.value,
  (v) => {
    const trimmed = v.trim();
    const m = /^(\d{4})-(\d{2})$/.exec(trimmed);
    if (!m || !props.items.some((i) => i.periodKey === trimmed)) {
      ensureYearInList();
      ensureMonthInYear();
      return;
    }
    selectedYear.value = m[1];
    selectedMonthKey.value = trimmed;
  },
  { immediate: true },
);

watch(
  () => props.items,
  () => {
    const trimmed = model.value.trim();
    const validModel = trimmed && props.items.some((i) => i.periodKey === trimmed);
    if (validModel) {
      const m = /^(\d{4})-\d{2}$/.exec(trimmed);
      if (m) {
        selectedYear.value = m[1];
        selectedMonthKey.value = trimmed;
      }
    } else {
      ensureYearInList();
      ensureMonthInYear();
    }
  },
  { deep: true },
);

watch(selectedYear, () => {
  ensureMonthInYear();
  emitModelFromMonth();
});

watch(selectedMonthKey, () => {
  emitModelFromMonth();
});

const hintEmpty = computed(() => props.items.length === 0);
</script>

<template>
  <div class="space-y-2">
    <div class="flex flex-wrap gap-3">
      <div class="min-w-[8rem] flex-1">
        <label class="mb-1 block text-xs font-medium text-muted-foreground">年份</label>
        <select
          v-model="selectedYear"
          class="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
          :disabled="disabled || years.length === 0"
        >
          <option value="">请选择</option>
          <option v-for="y in years" :key="y" :value="y">{{ y }} 年</option>
        </select>
      </div>
      <div class="min-w-[10rem] flex-1">
        <label class="mb-1 block text-xs font-medium text-muted-foreground">月份</label>
        <select
          v-model="selectedMonthKey"
          class="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
          :disabled="disabled || !selectedYear || monthsInYear.length === 0"
        >
          <option value="">请选择</option>
          <option v-for="row in monthsInYear" :key="row.periodKey" :value="row.periodKey">
            {{ row.periodKey.slice(5) }} 月<span v-if="row.name"> · {{ row.name }}</span>
          </option>
        </select>
      </div>
    </div>
    <p v-if="hintEmpty" class="text-xs text-[var(--warning)]">暂无已配置的考核月度，请先到「周期与评选」新增月度周期。</p>
    <p v-else-if="selectedYear && monthsInYear.length === 0" class="text-xs text-muted-foreground">
      该年份下没有可用的考核月度。
    </p>
  </div>
</template>
