<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import { performanceDetailCopy as c } from "@/composables/performanceDetailCopy";
import type { CultureDimensionDef, CultureReviewItem, PerformanceIndicator, PerformanceRecord, ReviewItem } from "@/types/api.interface";
import { applySchemeWeightPortion, calcWeightedScore, scoreGradeFromTotal } from "@/utils/performanceScoringMerge";

type FormRow = { indicatorName: string; score?: number; comment: string };

const props = defineProps<{
  rec: PerformanceRecord;
  indicators: PerformanceIndicator[];
  cultureDimensions: CultureDimensionDef[];
  learningDimensions: PerformanceIndicator[];
  scoringWeights: { performance: number; culture: number; learning: number } | null;
  matrixEditSelf: boolean;
  matrixEditManager: boolean;
  matrixEditDotted: boolean;
  dualSupervisorEdit?: boolean;
  readonlyReviewTotals: { self: number | null; manager: number | null; dotted: number | null };
  previewEditingTotal: number | null;
  previewManagerEditingTotal?: number | null;
  previewDottedEditingTotal?: number | null;
  previewMergedByIndex: (index: number) => number | null;
  previewMergedCultureByName: (dimensionName: string) => number | null;
  previewMergedCultureSum: number | null;
  previewMergedLearningByName: (dimName: string) => number | null;
  previewReviewMergedTotal: number | null;
  /** 与详情页基本信息/校准区一致的总分；有值时优先于组件内自行推算 */
  displayMergedTotalScore?: number | null;
  previewReviewMergedPerfOnlyTotal: number | null;
  matrixTemplateTotals: { self: number | null; manager: number | null; dotted: number | null };
  reviewFormTitle: string;
  formatScore: (v?: number | null) => string;
}>();

const formContent = defineModel<FormRow[]>("formContent", { required: true });
const dottedFormContent = defineModel<FormRow[]>("dottedFormContent", { default: () => [] });
const cultureForm = defineModel<CultureReviewItem[]>("cultureForm", { required: true });
const dottedCultureForm = defineModel<CultureReviewItem[]>("dottedCultureForm", { default: () => [] });
const learningForm = defineModel<CultureReviewItem[]>("learningForm", { required: true });
const dottedLearningForm = defineModel<CultureReviewItem[]>("dottedLearningForm", { default: () => [] });

const useSplitDottedForms = computed(
  () => !!(props.dualSupervisorEdit && props.matrixEditManager && props.matrixEditDotted && props.rec.dottedManagerId),
);
const personalSummary = defineModel<string>("personalSummary", { required: true });
const managerSummary = defineModel<string>("managerSummary", { required: true });
const dottedManagerSummary = defineModel<string>("dottedManagerSummary", { required: true });

const showPerf = computed(() => !props.scoringWeights || props.scoringWeights.performance > 0);
const showCulture = computed(() => !props.scoringWeights || props.scoringWeights.culture > 0);
const showLearning = computed(() => (!props.scoringWeights || props.scoringWeights.learning > 0) && props.learningDimensions.length > 0);

function navLabel(base: string, key: 'performance' | 'culture' | 'learning'): string {
  if (!props.scoringWeights) return base;
  return `${base}（${props.scoringWeights[key]}%）`;
}

function reviewLine(list: ReviewItem[] | undefined, indicatorName: string) {
  return list?.find((x) => x.indicatorName === indicatorName);
}

function cultureLine(list: CultureReviewItem[] | undefined, name: string) {
  return list?.find((x) => x.name === name);
}

function blockDecimalScoreKeydown(e: KeyboardEvent) {
  if ([".", ",", "e", "E", "+", "-"].includes(e.key)) {
    e.preventDefault();
  }
}

function parseScoreAsInteger(value: string): number | null {
  if (value.trim() === "") return null;
  const n = parseFloat(value);
  if (!Number.isFinite(n)) return null;
  return Math.round(n);
}

function handleScoreChange(index: number, value: string, target: "manager" | "dotted" = "manager") {
  const source = target === "dotted" && useSplitDottedForms.value ? dottedFormContent : formContent;
  const next = [...source.value];
  const row = next[index]!;
  const ind = props.indicators[index];
  if (!ind) return;
  const cap = indicatorMaxScore(ind);
  if (value === "") {
    next[index] = { indicatorName: row.indicatorName, comment: row.comment };
  } else {
    const n = parseScoreAsInteger(value);
    if (n === null) {
      next[index] = { ...row, score: undefined };
    } else {
      next[index] = { ...row, score: Math.min(cap, Math.max(0, n)) };
    }
  }
  source.value = next;
}

function dottedPerfRow(index: number) {
  return useSplitDottedForms.value ? dottedFormContent.value[index] : formContent.value[index];
}

function mergedDisplayForIndicator(index: number, name: string) {
  if (props.rec.dottedManagerId) {
    const live = props.previewMergedByIndex(index);
    if (live != null) return live;
    if (props.matrixEditManager || props.matrixEditDotted) {
      return null;
    }
  }
  return props.rec.reviewMergedIndicators?.find((x) => x.indicatorName === name)?.mergedScore ?? null;
}

const displayMatrixMergedTotal = computed(() => {
  const p = props.previewReviewMergedTotal;
  if (props.matrixEditManager || props.matrixEditDotted) {
    if (p != null && !Number.isNaN(p)) return p;
    return null;
  }
  if (props.displayMergedTotalScore != null && !Number.isNaN(props.displayMergedTotalScore)) {
    return props.displayMergedTotalScore;
  }
  if (p != null) return p;
  const r = props.rec;
  return r.reviewMergedTotal ?? r.totalScore ?? null;
});

/** 与「结果」列同源：无虚线时随表单预览分变化；有虚线时仅当双方数据可合成总分时才有值 */
const displayMatrixMergedGrade = computed((): "S" | "A" | "B" | "C" | null => {
  const t = displayMatrixMergedTotal.value;
  if (t == null || Number.isNaN(t)) return null;
  return scoreGradeFromTotal(t);
});

const displayMatrixMergedPerfOnlyTotal = computed(() => {
  const p = props.previewReviewMergedPerfOnlyTotal;
  if (props.matrixEditManager || props.matrixEditDotted) {
    return p;
  }
  if (p != null) return p;
  return null;
});

const displayCultureMergedSum = computed(() => {
  const p = props.previewMergedCultureSum;
  if (props.matrixEditManager || props.matrixEditDotted) {
    return p;
  }
  if (p != null) return p;
  return null;
});

function displayCultureMergedDimension(dimensionName: string): number | null {
  const r = props.rec;
  if (!r.dottedManagerId) {
    if (props.matrixEditManager) {
      const row = cultureForm.value.find((x) => x.name === dimensionName);
      if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
      return row.score;
    }
    const row = cultureLine(r.cultureManagerReview, dimensionName);
    const s = row?.score;
    return typeof s === "number" && !Number.isNaN(s) ? s : null;
  }
  const live = props.previewMergedCultureByName(dimensionName);
  if (live != null) return live;
  if (props.matrixEditManager || props.matrixEditDotted) return null;
  return null;
}

function indicatorMaxScore(ind: PerformanceIndicator) {
  const m = ind.maxScore;
  const raw = typeof m === "number" && !Number.isNaN(m) && m > 0 ? m : 100;
  return Math.min(100, raw);
}

function handleLearningScoreChange(idx: number, value: string) {
  const row = learningForm.value[idx];
  if (!row) return;
  const next = [...learningForm.value];
  if (value === "") {
    next[idx] = { ...row, score: 0 };
  } else {
    const n = parseScoreAsInteger(value);
    if (n === null) return;
    next[idx] = { ...row, score: Math.min(100, Math.max(0, n)) };
  }
  learningForm.value = next;
}

function learningLine(list: ReviewItem[] | undefined, name: string) {
  return list?.find((x) => x.indicatorName === name || (x as ReviewItem & { name?: string }).name === name);
}

/** 学习与成长：各分项得分按模板权重加权平均（与后端 calcWeightedScore 一致） */
function learningWeightedForRole(
  review: ReviewItem[] | undefined,
  editFlag: boolean,
  role: "self" | "manager" | "dotted" = "manager",
): number | null {
  const inds = props.learningDimensions;
  if (!inds.length) return null;
  const items: ReviewItem[] = [];
  for (const ind of inds) {
    let sc: number | undefined;
    if (editFlag) {
      const list =
        role === "dotted" && useSplitDottedForms.value
          ? dottedLearningForm.value
          : role === "self"
            ? learningForm.value
            : learningForm.value;
      const row = list.find((x) => x.name === ind.name);
      sc = row?.score as number | undefined;
    } else {
      const row = review?.find(
        (x) => x.indicatorName === ind.name || (x as ReviewItem & { name?: string }).name === ind.name,
      );
      sc = row?.score;
    }
    if (typeof sc !== "number" || Number.isNaN(sc)) return null;
    items.push({ indicatorName: ind.name, score: sc, comment: "" });
  }
  return calcWeightedScore(items, inds);
}

const learningSelfWeighted = computed(() =>
  learningWeightedForRole(props.rec.learningSelfReview, props.matrixEditSelf, "self"),
);
const learningManagerWeighted = computed(() =>
  learningWeightedForRole(props.rec.learningManagerReview, props.matrixEditManager, "manager"),
);
const learningDottedWeighted = computed(() =>
  props.rec.dottedManagerId
    ? learningWeightedForRole(props.rec.learningDottedManagerReview, props.matrixEditDotted, "dotted")
    : null,
);

const learningMergedWeighted = computed((): number | null => {
  const inds = props.learningDimensions;
  if (!inds.length) return null;
  const items: ReviewItem[] = [];
  for (const ind of inds) {
    const v = displayLearningMergedDimension(ind.name);
    if (v == null) return null;
    items.push({ indicatorName: ind.name, score: v, comment: "" });
  }
  return calcWeightedScore(items, inds);
});

/** 总分行：分项加权平均后再乘方案「学习与成长」占比（与综合分公式一致）；无方案占比时仍为百分制加权平均 */
function applyLearningSchemeWeight(internalRate: number | null): number | null {
  const sw = props.scoringWeights;
  if (sw != null && sw.learning > 0) {
    return applySchemeWeightPortion(internalRate, sw.learning);
  }
  return internalRate;
}

const learningSelfTotalDisplay = computed(() => applyLearningSchemeWeight(learningSelfWeighted.value));
const learningManagerTotalDisplay = computed(() => applyLearningSchemeWeight(learningManagerWeighted.value));
const learningDottedTotalDisplay = computed(() => applyLearningSchemeWeight(learningDottedWeighted.value));
const learningMergedTotalDisplay = computed(() => applyLearningSchemeWeight(learningMergedWeighted.value));

function displayLearningMergedDimension(dimName: string): number | null {
  const r = props.rec;
  if (!r.dottedManagerId) {
    if (props.matrixEditManager) {
      const row = learningForm.value.find((x) => x.name === dimName);
      if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
      return row.score;
    }
    const row = r.learningManagerReview?.find(
      (x) => x.indicatorName === dimName || (x as ReviewItem & { name?: string }).name === dimName,
    );
    const s = row?.score;
    return typeof s === "number" && !Number.isNaN(s) ? s : null;
  }
  const live = props.previewMergedLearningByName(dimName);
  if (live != null) return live;
  if (props.matrixEditManager || props.matrixEditDotted) return null;
  return null;
}

function handleCultureScoreChange(idx: number, value: string, maxScore: number, target: "manager" | "dotted" = "manager") {
  const source = target === "dotted" && useSplitDottedForms.value ? dottedCultureForm : cultureForm;
  const row = source.value[idx];
  if (!row) return;
  const next = [...source.value];
  if (value === "") {
    next[idx] = { ...row, score: 0 };
  } else {
    const n = parseScoreAsInteger(value);
    if (n === null) return;
    next[idx] = { ...row, score: Math.min(maxScore, Math.max(0, n)) };
  }
  source.value = next;
}

function handleLearningScoreChangeForRole(idx: number, value: string, target: "manager" | "dotted") {
  const source = target === "dotted" && useSplitDottedForms.value ? dottedLearningForm : learningForm;
  const row = source.value[idx];
  if (!row) return;
  const next = [...source.value];
  if (value === "") {
    next[idx] = { ...row, score: 0 };
  } else {
    const n = parseScoreAsInteger(value);
    if (n === null) return;
    next[idx] = { ...row, score: Math.min(100, Math.max(0, n)) };
  }
  source.value = next;
}

function cultureSum(list: CultureReviewItem[] | undefined, defs: CultureDimensionDef[]): number | null {
  if (!list?.length) return null;
  let t = 0;
  for (const def of defs) {
    const row = list.find((x) => x.name === def.name);
    if (row == null || row.score === undefined || row.score === null || Number.isNaN(Number(row.score))) return null;
    t += Number(row.score);
  }
  return t;
}

const cultureSelfSum = computed(() =>
  props.matrixEditSelf ? cultureSum(cultureForm.value, props.cultureDimensions) : cultureSum(props.rec.cultureSelfReview, props.cultureDimensions),
);
const cultureManagerSum = computed(() =>
  props.matrixEditManager ? cultureSum(cultureForm.value, props.cultureDimensions) : cultureSum(props.rec.cultureManagerReview, props.cultureDimensions),
);
const cultureDottedSum = computed(() =>
  props.matrixEditDotted
    ? cultureSum(
        useSplitDottedForms.value ? dottedCultureForm.value : cultureForm.value,
        props.cultureDimensions,
      )
    : cultureSum(props.rec.cultureDottedManagerReview, props.cultureDimensions),
);

function selfFinalScore() {
  if (props.matrixEditSelf) return props.previewEditingTotal;
  return props.readonlyReviewTotals.self;
}

function managerFinalScore() {
  if (props.matrixEditManager) {
    return props.previewManagerEditingTotal ?? props.previewEditingTotal;
  }
  return props.readonlyReviewTotals.manager;
}

function dottedFinalScore() {
  if (props.matrixEditDotted) {
    if (useSplitDottedForms.value) {
      return props.previewDottedEditingTotal ?? props.previewEditingTotal;
    }
    return props.previewEditingTotal;
  }
  return props.readonlyReviewTotals.dotted;
}

type MatrixSectionId = "perf" | "culture" | "learning" | "final";

const activeMatrixSection = ref<MatrixSectionId>("final");

const matrixNavItems = computed(() => {
  const items: { id: MatrixSectionId; href: string; label: string }[] = [];
  if (showPerf.value) {
    items.push({ id: "perf", href: "#matrix-perf", label: navLabel(c.matrixNavPerf, "performance") });
  }
  if (showCulture.value) {
    items.push({ id: "culture", href: "#matrix-culture", label: navLabel(c.matrixNavCulture, "culture") });
  }
  if (showLearning.value) {
    items.push({ id: "learning", href: "#matrix-learning", label: navLabel(c.matrixNavLearning, "learning") });
  }
  items.push({ id: "final", href: "#matrix-final", label: c.matrixNavFinal });
  return items;
});

function sectionIdFromElementId(elementId: string): MatrixSectionId | null {
  switch (elementId) {
    case "matrix-perf":
      return "perf";
    case "matrix-culture":
      return "culture";
    case "matrix-learning":
      return "learning";
    case "matrix-final":
      return "final";
    default:
      return null;
  }
}

function matrixNavClass(id: MatrixSectionId) {
  return activeMatrixSection.value === id
    ? "border-l-2 border-primary bg-primary/10 pl-[calc(0.5rem-2px)] font-semibold text-primary"
    : "border-l-2 border-transparent pl-2 text-muted-foreground hover:bg-muted/60 hover:text-foreground";
}

let matrixSectionObserver: IntersectionObserver | null = null;

function bindMatrixSectionObserver() {
  matrixSectionObserver?.disconnect();
  const ids = matrixNavItems.value.map((item) => item.href.replace(/^#/, ""));
  const elements = ids.map((id) => document.getElementById(id)).filter((el): el is HTMLElement => !!el);
  if (!elements.length) return;

  matrixSectionObserver = new IntersectionObserver(
    (entries) => {
      const visible = entries
        .filter((e) => e.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio);
      if (!visible.length) return;
      const sid = sectionIdFromElementId(visible[0]!.target.id);
      if (sid) activeMatrixSection.value = sid;
    },
    { root: null, rootMargin: "-15% 0px -55% 0px", threshold: [0.08, 0.2, 0.4] },
  );
  for (const el of elements) matrixSectionObserver.observe(el);
}

function onMatrixNavClick(id: MatrixSectionId) {
  activeMatrixSection.value = id;
}

onMounted(() => {
  const first = matrixNavItems.value[0];
  if (first) activeMatrixSection.value = first.id;
  bindMatrixSectionObserver();
});

onUnmounted(() => {
  matrixSectionObserver?.disconnect();
  matrixSectionObserver = null;
});

watch(matrixNavItems, () => {
  if (!matrixNavItems.value.some((item) => item.id === activeMatrixSection.value)) {
    activeMatrixSection.value = matrixNavItems.value[0]?.id ?? "final";
  }
  bindMatrixSectionObserver();
});
</script>

<template>
  <section class="rounded-md border border-border bg-card p-6 shadow-sm">
    <p v-if="rec.status !== 'completed'" class="mb-1 text-sm text-muted-foreground">{{ reviewFormTitle }}</p>
    <h2 class="mb-4 text-base font-semibold">{{ rec.status === "completed" ? c.matrixCompletedTitle : c.matrixScoringTitle }}</h2>

    <div class="flex flex-col gap-6 lg:flex-row lg:items-start">
      <aside class="shrink-0 space-y-1 border-b border-border pb-4 lg:w-44 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-4">
        <a
          v-for="item in matrixNavItems"
          :key="item.id"
          :href="item.href"
          class="block rounded-md py-1.5 pr-2 text-sm transition-colors"
          :class="matrixNavClass(item.id)"
          :aria-current="activeMatrixSection === item.id ? 'location' : undefined"
          @click="onMatrixNavClick(item.id)"
        >
          {{ item.label }}
        </a>
      </aside>

      <div class="min-w-0 flex-1 space-y-10">
        <div v-if="showPerf" id="matrix-perf" class="scroll-mt-28">
          <h3 class="mb-1 text-sm font-semibold">{{ navLabel(c.matrixNavPerf, 'performance') }}</h3>
          <p v-if="scoringWeights && scoringWeights.performance > 0" class="mb-2 text-xs text-muted-foreground">
            {{ c.matrixPerfCalcHint }}；总分行已乘方案绩效占比（{{ scoringWeights.performance }}%），与同表综合分中绩效部分一致。
          </p>
          <p v-else class="mb-2 text-xs text-muted-foreground">{{ c.matrixPerfCalcHint }}</p>
          <div
            class="w-full min-w-0 max-w-full overflow-x-auto overscroll-x-contain rounded-md border border-border bg-card shadow-sm [-webkit-overflow-scrolling:touch]"
          >
            <table class="ui-table min-w-[1240px]">
              <thead>
                <tr class="border-b border-border bg-muted/50">
                  <th class="min-w-[7rem] max-w-[12rem] p-3 text-left align-middle text-sm font-medium">{{ c.matrixColName }}</th>
                  <th class="w-20 shrink-0 p-3 text-center align-middle text-sm font-medium">{{ c.matrixColMaxScore }}</th>
                  <th class="min-w-[20rem] p-3 text-left align-middle text-sm font-medium">{{ c.matrixColCriteria }}</th>
                  <th class="w-24 shrink-0 p-3 text-center align-middle text-sm font-medium">{{ c.matrixColWeightPct }}</th>
                  <th class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium">{{ c.matrixColSelf }}</span>
                      <UserDisplay class="text-xs" :value="{ user_id: rec.employeeId, name: rec.employeeName || rec.employeeId }" />
                    </div>
                  </th>
                  <th class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium">{{ c.matrixColManager }}</span>
                      <UserDisplay class="text-xs" :value="{ user_id: rec.managerId, name: rec.managerName || rec.managerId }" />
                    </div>
                  </th>
                  <th v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] max-w-[11rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium leading-snug">{{ c.matrixColDotted }}</span>
                      <UserDisplay
                        class="text-xs"
                        :value="{ user_id: rec.dottedManagerId!, name: rec.dottedManagerName || rec.dottedManagerId }"
                      />
                    </div>
                  </th>
                  <th class="border-l border-border p-3 text-center align-middle text-sm font-medium">{{ c.matrixColResult }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(indicator, index) in indicators" :key="indicator.name" class="border-b border-border last:border-0">
                  <td class="p-3 align-top">
                    <p class="font-medium">{{ indicator.name }}</p>
                    <p v-if="indicator.description" class="mt-1 text-xs text-muted-foreground">{{ indicator.description }}</p>
                  </td>
                  <td class="p-2 align-top text-center text-sm tabular-nums text-muted-foreground">{{ indicatorMaxScore(indicator) }}</td>
                  <td class="min-w-[20rem] p-3 align-top text-sm leading-relaxed text-foreground">
                    <div class="max-h-[22rem] overflow-y-auto whitespace-pre-wrap text-[15px] leading-[1.65]">
                      {{ indicator.criteria?.trim() || "—" }}
                    </div>
                  </td>
                  <td class="p-3 align-top text-center text-sm font-medium tabular-nums">{{ indicator.weight }}%</td>
                  <td class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditSelf"
                      type="number"
                      step="1"
                      min="0"
                      :max="indicatorMaxScore(indicator)"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="formContent[index]?.score === undefined ? '' : String(formContent[index]!.score)"
                      :placeholder="c.scorePlaceholder"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleScoreChange(index, ($event.target as HTMLInputElement).value)"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(reviewLine(rec.selfReview, indicator.name)?.score)
                    }}</span>
                  </td>
                  <td class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditManager"
                      type="number"
                      step="1"
                      min="0"
                      :max="indicatorMaxScore(indicator)"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="formContent[index]?.score === undefined ? '' : String(formContent[index]!.score)"
                      :placeholder="c.scorePlaceholder"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleScoreChange(index, ($event.target as HTMLInputElement).value)"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(reviewLine(rec.managerReview, indicator.name)?.score)
                    }}</span>
                  </td>
                  <td v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditDotted"
                      type="number"
                      step="1"
                      min="0"
                      :max="indicatorMaxScore(indicator)"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="dottedPerfRow(index)?.score === undefined ? '' : String(dottedPerfRow(index)!.score)"
                      :placeholder="c.scorePlaceholder"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleScoreChange(index, ($event.target as HTMLInputElement).value, 'dotted')"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(reviewLine(rec.dottedManagerReview, indicator.name)?.score)
                    }}</span>
                  </td>
                  <td class="border-l border-border p-3 text-center align-middle text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(mergedDisplayForIndicator(index, indicator.name)) }}
                  </td>
                </tr>
              </tbody>
              <tfoot>
                <tr class="border-t-2 border-border bg-muted/30 font-medium">
                  <td colspan="4" class="p-3 text-sm text-foreground">{{ c.matrixTemplateTotalRowLabel }}</td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(props.matrixTemplateTotals.self) }}
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(props.matrixTemplateTotals.manager) }}
                  </td>
                  <td
                    v-if="rec.dottedManagerId"
                    class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary"
                  >
                    {{ formatScore(props.matrixTemplateTotals.dotted) }}
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(displayMatrixMergedPerfOnlyTotal) }}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>

        <div v-if="showCulture" id="matrix-culture" class="scroll-mt-28">
          <h3 class="mb-1 text-sm font-semibold">{{ navLabel(c.matrixNavCulture, 'culture') }}</h3>
          <p class="mb-2 text-xs text-muted-foreground">{{ c.matrixCultureCalcHint }}</p>
          <div
            class="w-full min-w-0 max-w-full overflow-x-auto overscroll-x-contain rounded-md border border-border bg-card shadow-sm [-webkit-overflow-scrolling:touch]"
          >
            <table class="ui-table min-w-[1180px]">
              <thead>
                <tr class="border-b border-border bg-muted/50">
                  <th class="min-w-[6rem] p-3 text-left align-middle text-sm font-medium">{{ c.matrixColName }}</th>
                  <th class="w-20 shrink-0 p-3 text-center align-middle text-sm font-medium">{{ c.matrixColMaxScore }}</th>
                  <th class="min-w-[16rem] p-3 text-left align-middle text-sm font-medium">{{ c.matrixCultureContent }}</th>
                  <th class="min-w-[18rem] p-3 text-left align-middle text-sm font-medium">{{ c.matrixCultureCriteriaCol }}</th>
                  <th class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium">{{ c.matrixColSelf }}</span>
                      <UserDisplay class="text-xs" :value="{ user_id: rec.employeeId, name: rec.employeeName || rec.employeeId }" />
                    </div>
                  </th>
                  <th class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium">{{ c.matrixColManager }}</span>
                      <UserDisplay class="text-xs" :value="{ user_id: rec.managerId, name: rec.managerName || rec.managerId }" />
                    </div>
                  </th>
                  <th v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] max-w-[11rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium leading-snug">{{ c.matrixColDotted }}</span>
                      <UserDisplay
                        class="text-xs"
                        :value="{ user_id: rec.dottedManagerId!, name: rec.dottedManagerName || rec.dottedManagerId }"
                      />
                    </div>
                  </th>
                  <th class="border-l border-border p-3 text-center align-middle text-sm font-medium">{{ c.matrixColResult }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, idx) in cultureDimensions" :key="item.name" class="border-b border-border last:border-0">
                  <td class="p-3 align-top">
                    <p class="font-medium">{{ item.name }}</p>
                  </td>
                  <td class="p-3 align-top text-center text-sm tabular-nums text-muted-foreground">{{ item.maxScore }}</td>
                  <td class="min-w-[16rem] p-3 align-top text-sm leading-relaxed text-foreground">
                    <div class="max-h-[22rem] overflow-y-auto whitespace-pre-wrap text-[15px] leading-[1.65]">{{ item.description }}</div>
                  </td>
                  <td class="min-w-[18rem] p-3 align-top text-sm leading-relaxed text-foreground">
                    <div class="max-h-[22rem] overflow-y-auto whitespace-pre-wrap text-[15px] leading-[1.65]">{{ item.criteria }}</div>
                  </td>
                  <td class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditSelf"
                      type="number"
                      step="1"
                      min="0"
                      :max="item.maxScore"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="cultureForm[idx]?.score ?? ''"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleCultureScoreChange(idx, ($event.target as HTMLInputElement).value, item.maxScore)"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(cultureLine(rec.cultureSelfReview, item.name)?.score)
                    }}</span>
                  </td>
                  <td class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditManager"
                      type="number"
                      step="1"
                      min="0"
                      :max="item.maxScore"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="cultureForm[idx]?.score ?? ''"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleCultureScoreChange(idx, ($event.target as HTMLInputElement).value, item.maxScore)"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(cultureLine(rec.cultureManagerReview, item.name)?.score)
                    }}</span>
                  </td>
                  <td v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditDotted"
                      type="number"
                      step="1"
                      min="0"
                      :max="item.maxScore"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="(useSplitDottedForms ? dottedCultureForm[idx] : cultureForm[idx])?.score ?? ''"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleCultureScoreChange(idx, ($event.target as HTMLInputElement).value, item.maxScore, 'dotted')"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(cultureLine(rec.cultureDottedManagerReview, item.name)?.score)
                    }}</span>
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(displayCultureMergedDimension(item.name)) }}
                  </td>
                </tr>
                <tr class="border-t border-border bg-muted/25 font-medium">
                  <td colspan="4" class="p-3 text-sm">{{ c.matrixCultureTotalRow }}</td>
                  <td class="border-l border-border p-3 text-center text-base tabular-nums text-primary">
                    {{ cultureSelfSum === null ? "—" : cultureSelfSum }}
                  </td>
                  <td class="border-l border-border p-3 text-center text-base tabular-nums text-primary">
                    {{ cultureManagerSum === null ? "—" : cultureManagerSum }}
                  </td>
                  <td v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center text-base tabular-nums text-primary">
                    {{ cultureDottedSum === null ? "—" : cultureDottedSum }}
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(displayCultureMergedSum) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="showLearning" id="matrix-learning" class="scroll-mt-28">
          <h3 class="mb-1 text-sm font-semibold">{{ navLabel(c.matrixNavLearning, 'learning') }}</h3>
          <p v-if="scoringWeights && scoringWeights.learning > 0" class="mb-2 text-xs text-muted-foreground">
            各项满分 100，按分项权重加权平均；总分行已乘方案学习与成长占比（{{ scoringWeights.learning }}%），与同表综合分中学习部分一致。
          </p>
          <p v-else class="mb-2 text-xs text-muted-foreground">各项满分 100，按权重加权平均</p>
          <div
            class="w-full min-w-0 max-w-full overflow-x-auto overscroll-x-contain rounded-md border border-border bg-card shadow-sm [-webkit-overflow-scrolling:touch]"
          >
            <table class="ui-table min-w-[1080px]">
              <thead>
                <tr class="border-b border-border bg-muted/50">
                  <th class="min-w-[7rem] max-w-[12rem] p-3 text-left align-middle text-sm font-medium">{{ c.matrixColName }}</th>
                  <th class="w-20 shrink-0 p-3 text-center align-middle text-sm font-medium">{{ c.matrixColMaxScore }}</th>
                  <th class="min-w-[16rem] p-3 text-left align-middle text-sm font-medium">说明</th>
                  <th class="w-24 shrink-0 p-3 text-center align-middle text-sm font-medium">{{ c.matrixColWeightPct }}</th>
                  <th class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium">{{ c.matrixColSelf }}</span>
                      <UserDisplay class="text-xs" :value="{ user_id: rec.employeeId, name: rec.employeeName || rec.employeeId }" />
                    </div>
                  </th>
                  <th class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium">{{ c.matrixColManager }}</span>
                      <UserDisplay class="text-xs" :value="{ user_id: rec.managerId, name: rec.managerName || rec.managerId }" />
                    </div>
                  </th>
                  <th v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center align-middle">
                    <div class="flex min-w-[9rem] max-w-[11rem] flex-col items-center gap-2">
                      <span class="text-sm font-medium leading-snug">{{ c.matrixColDotted }}</span>
                      <UserDisplay
                        class="text-xs"
                        :value="{ user_id: rec.dottedManagerId!, name: rec.dottedManagerName || rec.dottedManagerId }"
                      />
                    </div>
                  </th>
                  <th class="border-l border-border p-3 text-center align-middle text-sm font-medium">{{ c.matrixColResult }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(dim, idx) in learningDimensions" :key="dim.name" class="border-b border-border last:border-0">
                  <td class="p-3 align-top">
                    <p class="font-medium">{{ dim.name }}</p>
                  </td>
                  <td class="p-3 align-top text-center text-sm tabular-nums text-muted-foreground">100</td>
                  <td class="min-w-[16rem] p-3 align-top text-sm leading-relaxed text-foreground">
                    <div class="max-h-[22rem] overflow-y-auto whitespace-pre-wrap text-[15px] leading-[1.65]">{{ dim.description || "—" }}</div>
                  </td>
                  <td class="p-3 align-top text-center text-sm font-medium tabular-nums">{{ dim.weight }}%</td>
                  <td class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditSelf"
                      type="number"
                      step="1"
                      min="0"
                      max="100"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="learningForm[idx]?.score ?? ''"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleLearningScoreChange(idx, ($event.target as HTMLInputElement).value)"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(learningLine(rec.learningSelfReview, dim.name)?.score)
                    }}</span>
                  </td>
                  <td class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditManager"
                      type="number"
                      step="1"
                      min="0"
                      max="100"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="learningForm[idx]?.score ?? ''"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleLearningScoreChange(idx, ($event.target as HTMLInputElement).value)"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(learningLine(rec.learningManagerReview, dim.name)?.score)
                    }}</span>
                  </td>
                  <td v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center align-middle">
                    <input
                      v-if="matrixEditDotted"
                      type="number"
                      step="1"
                      min="0"
                      max="100"
                      class="mx-auto block min-h-11 min-w-[7.5rem] rounded-md border border-border bg-card px-3 py-2 text-center text-base tabular-nums shadow-sm"
                      :value="(useSplitDottedForms ? dottedLearningForm[idx] : learningForm[idx])?.score ?? ''"
                      @keydown="blockDecimalScoreKeydown"
                      @wheel.prevent
                      @input="handleLearningScoreChangeForRole(idx, ($event.target as HTMLInputElement).value, 'dotted')"
                    />
                    <span v-else class="inline-block min-h-11 min-w-[7.5rem] px-2 py-2 text-center text-base font-semibold tabular-nums text-primary">{{
                      formatScore(learningLine(rec.learningDottedManagerReview, dim.name)?.score)
                    }}</span>
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ formatScore(displayLearningMergedDimension(dim.name)) }}
                  </td>
                </tr>
                <tr class="border-t border-border bg-muted/25 font-medium">
                  <td colspan="4" class="p-3 text-sm">{{ c.matrixCultureTotalRow }}</td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ learningSelfTotalDisplay === null ? "—" : formatScore(learningSelfTotalDisplay) }}
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ learningManagerTotalDisplay === null ? "—" : formatScore(learningManagerTotalDisplay) }}
                  </td>
                  <td v-if="rec.dottedManagerId" class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ learningDottedTotalDisplay === null ? "—" : formatScore(learningDottedTotalDisplay) }}
                  </td>
                  <td class="border-l border-border p-3 text-center text-base font-semibold tabular-nums text-primary">
                    {{ learningMergedTotalDisplay === null ? "—" : formatScore(learningMergedTotalDisplay) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div id="matrix-final" class="scroll-mt-28">
          <h3 class="mb-2 text-sm font-semibold">{{ c.matrixNavFinal }}</h3>
          <div
            class="w-full min-w-0 max-w-full overflow-x-auto overscroll-x-contain rounded-md border border-border bg-card shadow-sm [-webkit-overflow-scrolling:touch]"
          >
            <table class="ui-table min-w-[1140px]">
              <thead>
                <tr class="border-b border-border bg-muted/50">
                  <th rowspan="2" class="w-40 p-3 text-left align-middle text-sm font-medium">{{ c.matrixColName }}</th>
                  <th colspan="2" class="border-l border-border p-3 text-center text-sm font-medium">{{ c.matrixColSelf }}</th>
                  <th colspan="2" class="border-l border-border p-3 text-center text-sm font-medium">{{ c.matrixColManager }}</th>
                  <th v-if="rec.dottedManagerId" colspan="2" class="border-l border-border p-3 text-center text-sm font-medium">
                    {{ c.matrixColDotted }}
                  </th>
                  <th rowspan="2" class="border-l border-border p-3 text-center align-middle text-sm font-medium">{{ c.matrixColResult }}</th>
                  <th rowspan="2" class="border-l border-border p-3 text-center align-middle text-sm font-medium">{{ c.matrixColGrade }}</th>
                </tr>
                <tr class="border-b border-border bg-muted/30 text-xs text-muted-foreground">
                  <th class="border-l border-border p-3 text-center font-normal">
                    {{ c.matrixScoreDetail }}<span v-if="matrixEditSelf" class="text-destructive">*</span>
                  </th>
                  <th class="min-w-[6.5rem] whitespace-nowrap p-3 text-center font-normal">{{ c.matrixScoreLabel }}</th>
                  <th class="border-l border-border p-3 text-center font-normal">{{ c.matrixScoreDetail }}</th>
                  <th class="min-w-[6.5rem] whitespace-nowrap p-3 text-center font-normal">{{ c.matrixScoreLabel }}</th>
                  <template v-if="rec.dottedManagerId">
                    <th class="border-l border-border p-3 text-center font-normal">{{ c.matrixScoreDetail }}</th>
                    <th class="min-w-[6.5rem] whitespace-nowrap p-3 text-center font-normal">{{ c.matrixScoreLabel }}</th>
                  </template>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="p-3 font-medium">{{ c.matrixTotalRow }}</td>
                  <td class="min-w-[18rem] border-l border-border p-3 align-top">
                    <div class="rounded-md border border-border/60 bg-muted/15 p-4">
                      <UserDisplay class="text-xs" :value="{ user_id: rec.employeeId, name: rec.employeeName || rec.employeeId }" />
                      <textarea
                        v-if="matrixEditSelf"
                        v-model="personalSummary"
                        rows="9"
                        class="mt-2 min-h-[12rem] w-full rounded-md border border-border bg-card px-3 py-2 text-sm leading-relaxed"
                        :placeholder="c.summaryPlaceholder"
                      />
                      <p v-else class="mt-2 max-h-64 overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed">
                        {{ rec.personalSummary?.trim() || c.notFilled }}
                      </p>
                    </div>
                  </td>
                  <td class="min-w-[7.5rem] border-l border-border p-4 text-center align-middle text-xl font-semibold tabular-nums text-primary">
                    {{ formatScore(selfFinalScore()) }}
                  </td>
                  <td class="min-w-[18rem] border-l border-border p-3 align-top">
                    <div class="rounded-md border border-border/60 bg-muted/15 p-4">
                      <UserDisplay class="text-xs" :value="{ user_id: rec.managerId, name: rec.managerName || rec.managerId }" />
                      <textarea
                        v-if="matrixEditManager"
                        v-model="managerSummary"
                        rows="9"
                        class="mt-2 min-h-[12rem] w-full rounded-md border border-border bg-card px-3 py-2 text-sm leading-relaxed"
                        :placeholder="c.managerSummaryPlaceholder"
                      />
                      <p v-else class="mt-2 max-h-64 overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed">
                        {{ rec.managerSummary?.trim() || c.notFilled }}
                      </p>
                    </div>
                  </td>
                  <td class="min-w-[7.5rem] border-l border-border p-4 text-center align-middle text-xl font-semibold tabular-nums text-primary">
                    {{ formatScore(managerFinalScore()) }}
                  </td>
                  <template v-if="rec.dottedManagerId">
                    <td class="min-w-[18rem] border-l border-border p-3 align-top">
                      <div class="rounded-md border border-border/60 bg-muted/15 p-4">
                        <UserDisplay
                          class="text-xs"
                          :value="{ user_id: rec.dottedManagerId!, name: rec.dottedManagerName || rec.dottedManagerId }"
                        />
                        <textarea
                          v-if="matrixEditDotted"
                          v-model="dottedManagerSummary"
                          rows="9"
                          class="mt-2 min-h-[12rem] w-full rounded-md border border-border bg-card px-3 py-2 text-sm leading-relaxed"
                          :placeholder="c.dottedManagerSummaryPlaceholder"
                        />
                        <p v-else class="mt-2 max-h-64 overflow-y-auto whitespace-pre-wrap text-sm leading-relaxed">
                          {{ rec.dottedManagerSummary?.trim() || c.notFilled }}
                        </p>
                      </div>
                    </td>
                    <td class="min-w-[7.5rem] border-l border-border p-4 text-center align-middle text-xl font-semibold tabular-nums text-primary">
                      {{ formatScore(dottedFinalScore()) }}
                    </td>
                  </template>
                  <td class="border-l border-border p-4 text-center align-middle text-xl font-semibold tabular-nums text-primary">
                    {{ formatScore(displayMatrixMergedTotal) }}
                  </td>
                  <td class="border-l border-border p-4 text-center align-middle text-xl font-semibold tabular-nums text-primary">
                    {{ displayMatrixMergedGrade ?? "—" }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
