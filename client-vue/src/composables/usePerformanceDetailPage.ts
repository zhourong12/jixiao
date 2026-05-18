import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { storeToRefs } from "pinia";
import type {
  CultureDimensionDef,
  CultureReviewItem,
  GoalSettingItem,
  PerformanceIndicator,
  PerformanceRecord,
  PerformanceStatus,
  ReviewItem,
  TemplateListItem,
} from "@/types/api.interface";
import { defaultCultureDimensions, recordCultureDimensions } from "@/types/api.interface";
import {
  approveGoal,
  calibratePerformance,
  confirmPerformanceResult,
  getPerformanceDetail,
  rejectPerformance,
  saveGoalIndicators,
  savePerformanceDraft,
  selectTemplate,
  submitPerformanceReview,
} from "@/api/performances";
import { listTemplates } from "@/api/templates";
import {
  PERFORMANCE_DETAIL_STATUS,
  PERFORMANCE_DETAIL_STEPS,
  performanceDetailStepIndex,
} from "@/constants/performanceDetailFlow";
import { performanceDetailCopy as detailCopy } from "@/composables/performanceDetailCopy";
import { shortPersonDisplayName } from "@/utils/user";
import { useSessionStore } from "@/stores/session";
import { useToast } from "@/composables/useToast";
import * as PS from "@/utils/performanceScoringMerge";

type ReviewFormRow = {
  indicatorName: string;
  score?: number;
  comment: string;
};

export function usePerformanceDetailPage() {
  const route = useRoute();
  const session = useSessionStore();
  const { userId, role } = storeToRefs(session);
  const toast = useToast();

  const rec = ref<PerformanceRecord | null>(null);
  const err = ref<string | null>(null);
  const loading = ref(true);
  const savingDraft = ref(false);
  const submittingGoal = ref(false);
  const submittingReview = ref(false);
  const rejectingSubordinate = ref(false);
  const approvingGoal = ref(false);
  const calibrating = ref(false);
  const confirmingResult = ref(false);
  const templates = ref<TemplateListItem[]>([]);
  const templateLoading = ref(false);
  const selecting = ref(false);
  const savingGoalIndicators = ref(false);
  const goalIndicatorDraft = ref<PerformanceIndicator[]>([]);
  const goalSettings = ref<GoalSettingItem[]>([]);
  const GOAL_WEIGHT_EPS = 0.02;
  const formContent = ref<ReviewFormRow[]>([]);
  const cultureForm = ref<CultureReviewItem[]>(
    defaultCultureDimensions().map((c) => ({ name: c.name, score: 0, comment: "" })),
  );
  const learningForm = ref<CultureReviewItem[]>([]);
  const personalSummary = ref("");
  const managerSummary = ref("");
  const dottedManagerSummary = ref("");
  const rejectReason = ref("");
  const rejectOpen = ref(false);
  const managerRejectOpen = ref(false);
  const managerRejectReason = ref("");
  const finalRejectOpen = ref(false);
  const finalReturnToStage = ref<PerformanceStatus>("self_review");

  const statusInfo = computed(() => (rec.value ? PERFORMANCE_DETAIL_STATUS[rec.value.status] : null));
  const currentStepIndex = computed(() => (rec.value ? performanceDetailStepIndex(rec.value.status) : -1));
  const progressValue = computed(() => {
    if (currentStepIndex.value < 0) return 0;
    return ((currentStepIndex.value + 1) / PERFORMANCE_DETAIL_STEPS.length) * 100;
  });
  const indicators = computed(() => rec.value?.indicators ?? []);
  const goalPerfWeightSum = computed(() =>
    goalIndicatorDraft.value.reduce((s, x) => s + (Number(x.weight) || 0), 0),
  );
  const currentUserId = computed(() => userId.value || "");
  const isSuperAdminUser = computed(() => role.value === "super_admin");
  const canFinalCalibrationUser = computed(
    () => session.allow("performance_review_admin") || isSuperAdminUser.value,
  );
  const isEmployee = computed(() => !!rec.value && currentUserId.value === rec.value.employeeId);
  const isManager = computed(() => !!rec.value && currentUserId.value === rec.value.managerId);
  const isDottedManager = computed(() => !!rec.value && currentUserId.value === rec.value.dottedManagerId);
  const canEditFinalReviewManager = computed(
    () =>
      rec.value?.status === "final_review" &&
      (isManager.value || (isDottedManager.value && !!rec.value.dottedManagerId)),
  );
  const canEdit = computed(() => {
    if (!rec.value) return false;
    return (
      (rec.value.status === "goal_setting" && isEmployee.value) ||
      (rec.value.status === "goal_rejected" && isEmployee.value) ||
      (rec.value.status === "self_review" && isEmployee.value) ||
      (rec.value.status === "manager_review" && isManager.value) ||
      (rec.value.status === "dual_manager_review" && (isManager.value || isDottedManager.value)) ||
      (rec.value.status === "dotted_manager_review" && isDottedManager.value) ||
      canEditFinalReviewManager.value
    );
  });
  const canSubmit = computed(() => {
    if (!rec.value) return false;
    return (
      (rec.value.status === "goal_setting" && isEmployee.value) ||
      (rec.value.status === "goal_rejected" && isEmployee.value) ||
      (rec.value.status === "self_review" && isEmployee.value) ||
      (rec.value.status === "manager_review" && isManager.value) ||
      (rec.value.status === "dual_manager_review" && (isManager.value || isDottedManager.value)) ||
      (rec.value.status === "dotted_manager_review" && isDottedManager.value) ||
      canEditFinalReviewManager.value
    );
  });
  const canGoalApprove = computed(
    () => rec.value?.status === "goal_pending_review" && (isManager.value || isDottedManager.value),
  );
  const canRejectSubordinateReview = computed(() => {
    if (!rec.value) return false;
    const s = rec.value.status;
    return (
      (s === "manager_review" && isManager.value) ||
      (s === "dual_manager_review" && (isManager.value || isDottedManager.value)) ||
      (s === "dotted_manager_review" && isDottedManager.value)
    );
  });
  const canFinalApprove = computed(() => rec.value?.status === "final_review" && canFinalCalibrationUser.value);
  const showScoresInFinalReview = computed(
    () => rec.value?.status === "final_review" && canFinalCalibrationUser.value,
  );
  const showWeightedScores = computed(() => {
    if (!rec.value) return false;
    return ["manager_review", "dual_manager_review", "dotted_manager_review", "final_review", "completed"].includes(
      rec.value.status,
    );
  });
  const showActionBar = computed(
    () => !!rec.value && (canEdit.value || canGoalApprove.value || canFinalApprove.value),
  );

  function formatScore(value?: number | null) {
    if (value == null || Number.isNaN(value)) return "-";
    return String(value);
  }

  function reviewItems(items?: ReviewItem[]) {
    return items ?? [];
  }

  function syncGoalIndicatorDraft(performance: PerformanceRecord) {
    if (
      ["goal_setting", "goal_rejected"].includes(performance.status) &&
      currentUserId.value === performance.employeeId &&
      performance.indicators?.length
    ) {
      const draft = JSON.parse(JSON.stringify(performance.indicators)) as PerformanceIndicator[];
      const goals = performance.goalSetting ?? [];
      for (const row of draft) {
        const g = goals.find((x) => x.indicatorName === row.name);
        row.criteria = g?.criteria ?? row.criteria ?? "";
        // 记录上的 weight 已为「占总分表中的绩效百分点」；编辑时不再改回模板内 100% 分拆，避免与评分方案口径脱节
      }
      goalIndicatorDraft.value = draft;
    } else {
      goalIndicatorDraft.value = [];
    }
  }

  function syncFormsFromRecord(performance: PerformanceRecord) {
    if (performance.indicators?.length) {
      const existingGoals = performance.goalSetting;
      goalSettings.value = performance.indicators.map((ind) => {
        const saved = existingGoals?.find((g) => g.indicatorName === ind.name);
        return {
          indicatorName: ind.name,
          weight: ind.weight,
          criteria: saved?.criteria ?? ind.criteria ?? "",
        };
      });
    } else {
      goalSettings.value = [];
    }

    if (!performance.indicators?.length) {
      formContent.value = [];
      personalSummary.value = performance.personalSummary ?? "";
      managerSummary.value = performance.managerSummary ?? "";
      dottedManagerSummary.value = performance.dottedManagerSummary ?? "";
      goalIndicatorDraft.value = [];
      return;
    }

    let existingContent: ReviewItem[] | undefined;
    if (currentUserId.value === performance.employeeId) {
      existingContent = performance.selfReview;
    } else if (currentUserId.value === performance.managerId) {
      existingContent = performance.managerReview;
    } else if (currentUserId.value === performance.dottedManagerId) {
      existingContent = performance.dottedManagerReview;
    }

    formContent.value = performance.indicators.map((ind) => {
      const existing = existingContent?.find((item) => item.indicatorName === ind.name);
      const hasScore =
        existing != null && typeof existing.score === "number" && !Number.isNaN(existing.score);
      return {
        indicatorName: ind.name,
        ...(hasScore ? { score: existing!.score } : {}),
        comment: existing?.comment ?? "",
      };
    });
    personalSummary.value = performance.personalSummary ?? "";
    managerSummary.value = performance.managerSummary ?? "";
    dottedManagerSummary.value = performance.dottedManagerSummary ?? "";

    let existingCulture: CultureReviewItem[] | undefined;
    if (currentUserId.value === performance.employeeId) {
      existingCulture = performance.cultureSelfReview;
    } else if (currentUserId.value === performance.managerId) {
      existingCulture = performance.cultureManagerReview;
    } else if (currentUserId.value === performance.dottedManagerId) {
      existingCulture = performance.cultureDottedManagerReview;
    }
    const dims = recordCultureDimensions(performance);
    cultureForm.value = dims.map((c) => {
      const existing = existingCulture?.find((item) => item.name === c.name);
      return {
        name: c.name,
        score: existing?.score ?? 0,
        comment: existing?.comment ?? "",
      };
    });

    let existingLearning: ReviewItem[] | undefined;
    if (currentUserId.value === performance.employeeId) {
      existingLearning = performance.learningSelfReview;
    } else if (currentUserId.value === performance.managerId) {
      existingLearning = performance.learningManagerReview;
    } else if (currentUserId.value === performance.dottedManagerId) {
      existingLearning = performance.learningDottedManagerReview;
    }
    const lDims = performance.learningDimensions ?? [];
    learningForm.value = lDims.map((ind) => {
      const existing = existingLearning?.find(
        (item) => item.indicatorName === ind.name || (item as ReviewItem & { name?: string }).name === ind.name,
      );
      return {
        name: ind.name,
        score: existing?.score ?? 0,
        comment: existing?.comment ?? "",
      };
    });
    syncGoalIndicatorDraft(performance);
  }

  function flushGoalDraftToGoalSettings() {
    if (!goalIndicatorDraft.value.length) return;
    const draft = goalIndicatorDraft.value;
    const sum = draft.reduce((s, r) => s + (Number(r.weight) || 0), 0);
    const swP = rec.value?.scoringWeights?.performance;
    const useTemplateScale =
      swP != null && swP > GOAL_WEIGHT_EPS && Math.abs(sum - 100) <= GOAL_WEIGHT_EPS;
    goalSettings.value = draft.map((row) => {
      const w = Number(row.weight) || 0;
      return {
        indicatorName: row.name,
        weight: useTemplateScale ? (swP * w) / 100 : w,
        criteria: row.criteria ?? "",
      };
    });
  }

  function validateGoalPerfWeightSum(list: PerformanceIndicator[], record: PerformanceRecord | null): string | null {
    const sum = list.reduce((s, x) => s + (Number(x.weight) || 0), 0);
    const sw = record?.scoringWeights;
    if (sw && typeof sw.performance === "number" && sw.performance > GOAL_WEIGHT_EPS) {
      if (Math.abs(sum - sw.performance) <= GOAL_WEIGHT_EPS) {
        return null;
      }
      return `各绩效指标「权重(%)」相加须等于评分方案中的绩效占比 ${sw.performance}%（当前合计 ${sum}%）`;
    }
    if (Math.abs(sum - 100) > GOAL_WEIGHT_EPS) {
      return `各绩效指标权重合计须为 100%（当前合计 ${sum}%）`;
    }
    return null;
  }

  function validateCultureLearningAgainstScheme(record: PerformanceRecord | null): string | null {
    const sw = record?.scoringWeights;
    if (!sw) return null;
    const cDims = recordCultureDimensions(record);
    if (sw.culture > GOAL_WEIGHT_EPS && cDims.length > 0) {
      const sumC = cDims.reduce((s, d) => s + (Number(d.maxScore) || 0), 0);
      if (sumC <= 0) {
        return `文化价值观快照各维满分合计须大于 0（当前 ${sumC}）。`;
      }
    }
    const lDims = record?.learningDimensions ?? [];
    if (sw.learning > GOAL_WEIGHT_EPS && lDims.length > 0) {
      const sumMax = lDims.reduce((s, d) => s + (Number(d.maxScore) || 0), 0);
      const sumW = lDims.reduce((s, d) => s + (Number(d.weight) || 0), 0);
      if (sumMax <= 0 && sumW <= 0) {
        return `学习与成长快照：各维满分或权重合计须大于 0（当前满分合计 ${sumMax}，权重合计 ${sumW}）`;
      }
    }
    return null;
  }

  function validateGoalIndicatorDraftForSave(
    record: PerformanceRecord | null,
    list: PerformanceIndicator[],
  ): string | null {
    if (!list.length) return "请至少保留一项绩效指标";
    const wErr = validateGoalPerfWeightSum(list, record);
    if (wErr) return wErr;
    for (const row of list) {
      if (!String(row.name || "").trim()) return "指标名称不能为空";
    }
    const names = new Set<string>();
    for (const row of list) {
      const n = String(row.name || "").trim();
      if (names.has(n)) return "指标名称不能重复";
      names.add(n);
    }
    return null;
  }

  async function persistGoalIndicators() {
    if (!rec.value) return;
    const list = goalIndicatorDraft.value;
    const err = validateGoalIndicatorDraftForSave(rec.value, list);
    if (err) {
      toast.error(err);
      return;
    }
    savingGoalIndicators.value = true;
    try {
      await saveGoalIndicators(rec.value.id, { indicators: list });
      toast.success("绩效指标已保存");
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      savingGoalIndicators.value = false;
    }
  }

  function addGoalIndicatorRow() {
    goalIndicatorDraft.value.push({ name: "", weight: 0, description: "", criteria: "" });
  }

  function removeGoalIndicatorRow(idx: number) {
    if (goalIndicatorDraft.value.length <= 1) return;
    goalIndicatorDraft.value.splice(idx, 1);
  }

  async function startCustomGoalIndicators() {
    if (!rec.value) return;
    savingGoalIndicators.value = true;
    try {
      const p = rec.value.scoringWeights?.performance;
      const w =
        typeof p === "number" && p > GOAL_WEIGHT_EPS && p <= 100 + GOAL_WEIGHT_EPS ? p : 100;
      await saveGoalIndicators(rec.value.id, {
        indicators: [{ name: "指标1", weight: w, description: "", criteria: "" }],
      });
      toast.success("已添加自定义指标，可继续增删并保存");
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "创建失败");
    } finally {
      savingGoalIndicators.value = false;
    }
  }

  async function load() {
    const id = route.params.id as string;
    loading.value = true;
    err.value = null;
    try {
      rec.value = await getPerformanceDetail(id);
      syncFormsFromRecord(rec.value);
      if (
        !["goal_setting", "goal_rejected"].includes(rec.value.status) ||
        currentUserId.value !== rec.value.employeeId
      ) {
        templates.value = [];
      }
    } catch (e) {
      err.value = e instanceof Error ? e.message : "\u52a0\u8f7d\u5931\u8d25";
      rec.value = null;
    } finally {
      loading.value = false;
    }
  }

  watch(() => route.params.id, () => void load(), { immediate: true });
  watch([rec, currentUserId], () => {
    if (rec.value) syncFormsFromRecord(rec.value);
  });

  function handleScoreChange(index: number, value: string) {
    const inds = rec.value?.indicators;
    const ind = inds?.[index];
    const next = [...formContent.value];
    const row = next[index]!;
    const cap = ind
      ? Math.min(100, typeof ind.maxScore === "number" && !Number.isNaN(ind.maxScore) && ind.maxScore > 0 ? ind.maxScore : 100)
      : 100;
    if (value === "") {
      next[index] = { ...next[index]!, score: undefined };
    } else {
      const n = parseFloat(value);
      if (!Number.isFinite(n)) {
        next[index] = { ...row, score: undefined };
      } else {
        const clamped = Math.min(cap, Math.max(0, n));
        next[index] = { ...row, score: Math.round(clamped * 100) / 100 };
      }
    }
    formContent.value = next;
  }

  function handleLearningScoreChange(index: number, value: string) {
    const next = [...learningForm.value];
    const row = next[index];
    if (!row) return;
    if (value === "") {
      next[index] = { ...row, score: 0 };
    } else {
      const n = parseFloat(value);
      if (!Number.isFinite(n)) {
        next[index] = { ...row, score: 0 };
      } else {
        next[index] = { ...row, score: Math.min(100, Math.max(0, Math.round(n * 100) / 100)) };
      }
    }
    learningForm.value = next;
  }

  function handleLearningCommentChange(index: number, value: string) {
    const next = [...learningForm.value];
    if (!next[index]) return;
    next[index] = { ...next[index]!, comment: value };
    learningForm.value = next;
  }

  function handleCommentChange(index: number, value: string) {
    const next = [...formContent.value];
    next[index] = { ...next[index]!, comment: value };
    formContent.value = next;
  }

  async function handleSaveDraft() {
    if (!rec.value) return;
    savingDraft.value = true;
    try {
      const isGoalStage = rec.value.status === "goal_setting" || rec.value.status === "goal_rejected";
      let reviewType: "goal" | "self" | "manager" | "dotted_manager";
      let content: ReviewItem[] | GoalSettingItem[];
      if (isGoalStage) {
        flushGoalDraftToGoalSettings();
        reviewType = "goal";
        content = goalSettings.value;
      } else if (rec.value.status === "self_review") {
        reviewType = "self";
        content = JSON.parse(JSON.stringify(formContent.value)) as ReviewItem[];
      } else if (rec.value.status === "manager_review") {
        reviewType = "manager";
        content = JSON.parse(JSON.stringify(formContent.value)) as ReviewItem[];
      } else if (rec.value.status === "dual_manager_review") {
        reviewType = currentUserId.value === rec.value.managerId ? "manager" : "dotted_manager";
        content = JSON.parse(JSON.stringify(formContent.value)) as ReviewItem[];
      } else if (rec.value.status === "dotted_manager_review") {
        reviewType = "dotted_manager";
        content = JSON.parse(JSON.stringify(formContent.value)) as ReviewItem[];
      } else if (rec.value.status === "final_review") {
        reviewType = currentUserId.value === rec.value.managerId ? "manager" : "dotted_manager";
        content = JSON.parse(JSON.stringify(formContent.value)) as ReviewItem[];
      } else {
        reviewType = "self";
        content = JSON.parse(JSON.stringify(formContent.value)) as ReviewItem[];
      }
      await savePerformanceDraft(rec.value.id, {
        reviewType,
        content,
        ...(reviewType !== "goal" ? { cultureContent: cultureForm.value } : {}),
        ...(reviewType !== "goal" && learningForm.value.length > 0 ? { learningContent: learningForm.value } : {}),
        ...(reviewType === "self" ? { personalSummary: personalSummary.value } : {}),
        ...(reviewType === "manager" ? { managerSummary: managerSummary.value } : {}),
        ...(reviewType === "dotted_manager" ? { dottedManagerSummary: dottedManagerSummary.value } : {}),
      });
      toast.success("草稿已保存");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存草稿失败");
    } finally {
      savingDraft.value = false;
    }
  }

  async function handleSubmitGoal() {
    if (!rec.value) return;
    submittingGoal.value = true;
    try {
      const structErr = validateGoalIndicatorDraftForSave(rec.value, goalIndicatorDraft.value);
      if (structErr) {
        toast.error(structErr);
        return;
      }
      const schemeErr = validateCultureLearningAgainstScheme(rec.value);
      if (schemeErr) {
        toast.error(schemeErr);
        return;
      }
      await saveGoalIndicators(rec.value.id, { indicators: goalIndicatorDraft.value });
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
      await submitPerformanceReview(rec.value.id, { reviewType: "goal", content: goalSettings.value });
      toast.success("目标已提交，等待上级审核");
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "提交目标失败");
    } finally {
      submittingGoal.value = false;
    }
  }

  async function handleRejectSubordinateReview() {
    if (!rec.value) return;
    if (!managerRejectReason.value.trim()) {
      toast.error("请输入驳回原因");
      return;
    }
    rejectingSubordinate.value = true;
    try {
      await rejectPerformance(rec.value.id, { reason: managerRejectReason.value.trim() });
      toast.success("已驳回，员工需修改自评后重新提交");
      managerRejectOpen.value = false;
      managerRejectReason.value = "";
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "驳回失败");
    } finally {
      rejectingSubordinate.value = false;
    }
  }

  async function handleApproveGoal(approved: boolean) {
    if (!rec.value) return;
    if (!approved && !rejectReason.value.trim()) {
      toast.error("请输入驳回原因");
      return;
    }
    approvingGoal.value = true;
    try {
      await approveGoal(rec.value.id, {
        approved,
        rejectionReason: approved ? undefined : rejectReason.value.trim(),
      });
      toast.success(approved ? "目标已批准" : "目标已驳回");
      rejectOpen.value = false;
      rejectReason.value = "";
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : approved ? "批准目标失败" : "驳回目标失败");
    } finally {
      approvingGoal.value = false;
    }
  }

  async function handleSubmitReview() {
    if (!rec.value) return;
    const incomplete = formContent.value.filter(
      (item) => item.score === undefined || item.score === null || Number.isNaN(item.score),
    );
    if (incomplete.length > 0) {
      toast.error("请为所有指标填写评分");
      return;
    }
    const outOfRange = formContent.value.filter((item) => (item.score as number) < 0 || (item.score as number) > 100);
    if (outOfRange.length > 0) {
      toast.error("评分需在 0～100 之间");
      return;
    }
    const reviewType: "self" | "manager" | "dotted_manager" =
      rec.value.status === "self_review"
        ? "self"
        : rec.value.status === "manager_review"
          ? "manager"
          : rec.value.status === "dual_manager_review"
            ? currentUserId.value === rec.value.managerId
              ? "manager"
              : "dotted_manager"
            : rec.value.status === "dotted_manager_review"
              ? "dotted_manager"
              : rec.value.status === "final_review"
                ? currentUserId.value === rec.value.managerId
                  ? "manager"
                  : "dotted_manager"
                : "dotted_manager";
    submittingReview.value = true;
    try {
      const content: ReviewItem[] = formContent.value.map((c) => ({
        indicatorName: c.indicatorName,
        score: c.score!,
        comment: c.comment ?? "",
      }));
      await submitPerformanceReview(rec.value.id, {
        reviewType,
        content,
        cultureContent: cultureForm.value,
        ...(learningForm.value.length > 0 ? { learningContent: learningForm.value } : {}),
        ...(reviewType === "self" ? { personalSummary: personalSummary.value } : {}),
        ...(reviewType === "manager" ? { managerSummary: managerSummary.value } : {}),
        ...(reviewType === "dotted_manager" ? { dottedManagerSummary: dottedManagerSummary.value } : {}),
      });
      toast.success("提交成功");
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "提交失败");
    } finally {
      submittingReview.value = false;
    }
  }

  async function onSelectTemplate(templateId: string): Promise<boolean> {
    if (!rec.value || selecting.value) return false;
    selecting.value = true;
    try {
      await selectTemplate(rec.value.id, { templateId });
      toast.success("模板选择成功");
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
      return true;
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "选择模板失败");
      return false;
    } finally {
      selecting.value = false;
    }
  }

  async function ensureGoalTemplatesLoaded() {
    if (templateLoading.value || templates.value.length > 0) return;
    const r = rec.value;
    if (!r || !["goal_setting", "goal_rejected"].includes(r.status)) return;
    if (currentUserId.value !== r.employeeId) return;
    templateLoading.value = true;
    try {
      const res = await listTemplates(1, 500, "performance");
      templates.value = (res.items ?? []).filter((t) => t.status === "enabled");
    } finally {
      templateLoading.value = false;
    }
  }

  async function handleCalibrate(approved: boolean) {
    if (!rec.value) return;
    if (!approved && !rejectReason.value.trim()) {
      toast.error("请输入驳回原因");
      return;
    }
    calibrating.value = true;
    try {
      await calibratePerformance(rec.value.id, {
        approved,
        rejectionReason: approved ? undefined : rejectReason.value.trim(),
        returnToStage: approved ? undefined : finalReturnToStage.value,
      });
      toast.success(approved ? "校准通过，结果已下发" : "已驳回");
      finalRejectOpen.value = false;
      rejectReason.value = "";
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : approved ? "校准通过失败" : "驳回失败");
    } finally {
      calibrating.value = false;
    }
  }

  async function handleConfirmResult() {
    if (!rec.value) return;
    confirmingResult.value = true;
    try {
      await confirmPerformanceResult(rec.value.id);
      toast.success("已确认绩效结果");
      rec.value = await getPerformanceDetail(rec.value.id);
      syncFormsFromRecord(rec.value);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "确认失败");
    } finally {
      confirmingResult.value = false;
    }
  }

  function reviewFormTitle() {
    if (!rec.value) return "";
    if (rec.value.status === "self_review") return "\u5458\u5de5\u81ea\u8bc4";
    if (rec.value.status === "manager_review") return "\u76f4\u5c5e\u4e0a\u7ea7\u8bc4\u5206";
    if (rec.value.status === "dual_manager_review") {
      return isManager.value ? "\u76f4\u5c5e\u4e0a\u7ea7\u8bc4\u5206" : "\u865a\u7ebf\u4e0a\u7ea7\u8bc4\u5206";
    }
    if (rec.value.status === "dotted_manager_review") return "\u865a\u7ebf\u4e0a\u7ea7\u8bc4\u5206";
    if (rec.value.status === "final_review") {
      return isManager.value
        ? "\u76f4\u5c5e\u4e0a\u7ea7\u8bc4\u5206\uff08\u6821\u51c6\u524d\u53ef\u4fee\u6539\uff09"
        : "\u865a\u7ebf\u4e0a\u7ea7\u8bc4\u5206\uff08\u6821\u51c6\u524d\u53ef\u4fee\u6539\uff09";
    }
    return "\u8bc4\u5206";
  }

  function personLabel(id?: string, name?: string) {
    const label = shortPersonDisplayName((name || "").trim());
    if (label) return label;
    const fallback = (id || "").trim();
    return fallback || "-";
  }

  function stepResponsibleLabel(stepKey: string) {
    if (!rec.value) return "-";
    switch (stepKey) {
      case "template":
      case "goal":
      case "self":
      case "confirm":
        return personLabel(rec.value.employeeId, rec.value.employeeName);
      case "goal_review": {
        const direct = personLabel(rec.value.managerId, rec.value.managerName);
        if (!rec.value.dottedManagerId) return direct;
        const dotted = personLabel(rec.value.dottedManagerId, rec.value.dottedManagerName);
        return `${direct} / ${dotted}`;
      }
      case "manager": {
        const direct = personLabel(rec.value.managerId, rec.value.managerName);
        if (!rec.value.dottedManagerId) return direct;
        const dotted = personLabel(rec.value.dottedManagerId, rec.value.dottedManagerName);
        return `${direct} / ${dotted}`;
      }
      case "final": {
        const list = rec.value.calibrationAssignees;
        if (list?.length) {
          const parts = list
            .map((x) => personLabel(x.employeeId, x.name))
            .filter((s) => s && s !== "-");
          if (parts.length) return parts.join("\u3001");
        }
        return "\u7ba1\u7406\u5458";
      }
      default:
        return "-";
    }
  }

  function hasCompletePersistedReview(performance: PerformanceRecord, role: "self" | "manager" | "dotted_manager") {
    const n = performance.indicators?.length ?? 0;
    if (!n) return false;
    const items =
      role === "self"
        ? performance.selfReview
        : role === "manager"
          ? performance.managerReview
          : performance.dottedManagerReview;
    return !!(
      items &&
      items.length === n &&
      items.every((i) => typeof i.score === "number" && !Number.isNaN(i.score as number))
    );
  }

  const activeReviewRoleForSubmit = computed((): "self" | "manager" | "dotted_manager" | null => {
    const r = rec.value;
    if (!r) return null;
    if (r.status === "self_review" && isEmployee.value) return "self";
    if (r.status === "manager_review" && isManager.value) return "manager";
    if (r.status === "dual_manager_review") {
      if (isManager.value) return "manager";
      if (isDottedManager.value) return "dotted_manager";
      return null;
    }
    if (r.status === "dotted_manager_review" && isDottedManager.value) return "dotted_manager";
    if (r.status === "final_review") {
      if (isManager.value) return "manager";
      if (isDottedManager.value && r.dottedManagerId) return "dotted_manager";
      return null;
    }
    return null;
  });

  const submitReviewButtonLabel = computed(() => {
    const r = rec.value;
    if (!r || !canSubmit.value) return detailCopy.submit;
    const role = activeReviewRoleForSubmit.value;
    if (!role) return detailCopy.submit;
    const submitted = hasCompletePersistedReview(r, role);
    if (role === "self") {
      return submitted ? detailCopy.submitReviewUpdate : detailCopy.submitSelfFirst;
    }
    return submitted ? detailCopy.submitReviewUpdate : detailCopy.submitReviewFirst;
  });

  function reviewLine(list: ReviewItem[] | undefined, indicatorName: string) {
    return list?.find((x) => x.indicatorName === indicatorName);
  }

  function cultureLine(list: CultureReviewItem[] | undefined, name: string) {
    return list?.find((x) => x.name === name);
  }

  /** 绩效得分率（加权平均，百分制） */
  function perfScoreRate(perf: ReviewItem[] | undefined, inds: PerformanceIndicator[] | undefined): number | null {
    if (!inds?.length) return null;
    return PS.calcWeightedScore(perf ?? null, inds);
  }

  /** 仅绩效指标按模板权重折算到百分制中的「绩效部分」，不含文化价值观加和 */
  function weightedPerfOnlyFromReviews(perf: ReviewItem[] | undefined, inds: PerformanceIndicator[] | undefined): number | null {
    if (!inds?.length) return null;
    const rate = perfScoreRate(perf, inds);
    if (rate === null) return null;
    return PS.round2(rate);
  }

  function singleReviewerTotal(
    perf: ReviewItem[] | undefined,
    inds: PerformanceIndicator[] | undefined,
    culture: CultureReviewItem[] | undefined,
    learning: ReviewItem[] | undefined,
    lInds: PerformanceIndicator[] | undefined,
    sw: PS.ScoringWeights | null | undefined,
    cDims: CultureDimensionDef[] | undefined,
  ): number | null {
    if (!inds?.length) return null;
    if (sw) {
      const pRate = perfScoreRate(perf, inds);
      const cRate = PS.cultureScoreRate(culture, cDims);
      const lRate = PS.learningScoreRate(learning ?? null, lInds);
      return PS.computeSingleReviewerTotalWithWeights(pRate, cRate, lRate, sw);
    }
    const perfPart = weightedPerfOnlyFromReviews(perf, inds);
    if (perfPart == null) return null;
    const pf = PS.perfWeightFactorFromIndicators(inds);
    const cultSum = PS.sumCultureScores(culture);
    return PS.round2(perfScoreRate(perf, inds)! * pf + cultSum);
  }

  function weightedPerfTotalFromReviews(
    perf: ReviewItem[] | undefined,
    inds: PerformanceIndicator[] | undefined,
    culture: CultureReviewItem[] | undefined,
    learning?: ReviewItem[],
    lInds?: PerformanceIndicator[],
    sw?: PS.ScoringWeights | null,
    cDims?: CultureDimensionDef[],
  ): number | null {
    return singleReviewerTotal(perf, inds, culture, learning, lInds, sw, cDims);
  }

  const showUnifiedReviewMatrix = computed(() => {
    const r = rec.value;
    if (!r || !indicators.value.length) return false;
    return ["self_review", "manager_review", "dual_manager_review", "dotted_manager_review", "final_review", "completed", "issued"].includes(
      r.status,
    );
  });

  const showEmployeeSubmittedSelfReadonly = computed(
    () =>
      !!rec.value &&
      ((rec.value.selfReview?.length ?? 0) > 0 ||
        !!(rec.value.personalSummary && String(rec.value.personalSummary).trim())),
  );

  const previewEditingPerfOnlyTotal = computed(() => {
    const inds = rec.value?.indicators;
    if (!inds?.length || formContent.value.length < inds.length) return null;
    const asReview: ReviewItem[] = formContent.value.map((fc) => ({
      indicatorName: fc.indicatorName,
      score: fc.score as number,
      comment: fc.comment,
    }));
    return weightedPerfOnlyFromReviews(asReview, inds);
  });

  const previewEditingTotal = computed(() => {
    const inds = rec.value?.indicators;
    if (!inds?.length) return null;
    const asReview: ReviewItem[] = formContent.value.map((fc) => ({
      indicatorName: fc.indicatorName,
      score: fc.score as number,
      comment: fc.comment,
    }));
    const sw = scoringWeights.value;
    const lInds = learningDimensions.value;
    const lReview: ReviewItem[] = learningForm.value.map((lf) => ({
      indicatorName: lf.name,
      score: lf.score as number,
      comment: lf.comment ?? "",
    }));
    return singleReviewerTotal(
      asReview, inds, cultureForm.value,
      lReview, lInds, sw, cultureDimensions.value,
    );
  });

  const readonlyReviewTotals = computed(() => {
    const r = rec.value;
    if (!r?.indicators?.length) {
      return { self: null as number | null, manager: null as number | null, dotted: null as number | null };
    }
    const sw = scoringWeights.value;
    const lInds = learningDimensions.value;
    const cDims = cultureDimensions.value;
    return {
      self: weightedPerfTotalFromReviews(r.selfReview, r.indicators, r.cultureSelfReview, r.learningSelfReview, lInds, sw, cDims),
      manager: weightedPerfTotalFromReviews(r.managerReview, r.indicators, r.cultureManagerReview, r.learningManagerReview, lInds, sw, cDims),
      dotted: weightedPerfTotalFromReviews(r.dottedManagerReview, r.indicators, r.cultureDottedManagerReview, r.learningDottedManagerReview, lInds, sw, cDims),
    };
  });

  const readonlyPerfOnlyTotals = computed(() => {
    const r = rec.value;
    if (!r?.indicators?.length) {
      return { self: null as number | null, manager: null as number | null, dotted: null as number | null };
    }
    return {
      self: weightedPerfOnlyFromReviews(r.selfReview, r.indicators),
      manager: weightedPerfOnlyFromReviews(r.managerReview, r.indicators),
      dotted: weightedPerfOnlyFromReviews(r.dottedManagerReview, r.indicators),
    };
  });

  const matrixEditSelf = computed(() => rec.value?.status === "self_review" && isEmployee.value && canEdit.value);
  const matrixEditManager = computed(
    () =>
      !!rec.value &&
      canEdit.value &&
      isManager.value &&
      ["manager_review", "dual_manager_review", "final_review"].includes(rec.value.status),
  );
  const matrixEditDotted = computed(
    () =>
      !!rec.value &&
      canEdit.value &&
      isDottedManager.value &&
      !!rec.value.dottedManagerId &&
      ["dual_manager_review", "dotted_manager_review", "final_review"].includes(rec.value.status),
  );

  const liveMergeWeights = computed(() =>
    PS.normalizeRoleWeights(rec.value?.reviewRoleWeights?.managerWeight, rec.value?.reviewRoleWeights?.dottedWeight),
  );

  function managerIndicatorScoreAt(r: PerformanceRecord, index: number): number | null {
    const ind = r.indicators?.[index];
    if (!ind) return null;
    if (matrixEditManager.value) {
      const row = formContent.value[index];
      if (!row || row.indicatorName !== ind.name) return null;
      const s = row.score;
      if (s === undefined || s === null || Number.isNaN(s)) return null;
      return s;
    }
    const ri = r.managerReview?.find((x) => x.indicatorName === ind.name);
    if (ri == null || typeof ri.score !== "number" || Number.isNaN(ri.score)) return null;
    return ri.score;
  }

  function dottedIndicatorScoreAt(r: PerformanceRecord, index: number): number | null {
    const ind = r.indicators?.[index];
    if (!ind || !r.dottedManagerId) return null;
    if (matrixEditDotted.value) {
      const row = formContent.value[index];
      if (!row || row.indicatorName !== ind.name) return null;
      const s = row.score;
      if (s === undefined || s === null || Number.isNaN(s)) return null;
      return s;
    }
    const ri = r.dottedManagerReview?.find((x) => x.indicatorName === ind.name);
    if (ri == null || typeof ri.score !== "number" || Number.isNaN(ri.score)) return null;
    return ri.score;
  }

  function managerCultureScoreAt(r: PerformanceRecord, dimensionName: string): number | null {
    if (matrixEditManager.value) {
      const row = cultureForm.value.find((x) => x.name === dimensionName);
      if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
      return row.score;
    }
    const row = r.cultureManagerReview?.find((x) => x.name === dimensionName);
    if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
    return row.score;
  }

  function dottedCultureScoreAt(r: PerformanceRecord, dimensionName: string): number | null {
    if (!r.dottedManagerId) return null;
    if (matrixEditDotted.value) {
      const row = cultureForm.value.find((x) => x.name === dimensionName);
      if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
      return row.score;
    }
    const row = r.cultureDottedManagerReview?.find((x) => x.name === dimensionName);
    if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
    return row.score;
  }

  function managerLearningScoreAt(r: PerformanceRecord, dimName: string): number | null {
    if (matrixEditManager.value) {
      const row = learningForm.value.find((x) => x.name === dimName);
      if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
      return row.score;
    }
    const row = r.learningManagerReview?.find(
      (x) => x.indicatorName === dimName || (x as ReviewItem & { name?: string }).name === dimName,
    );
    if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
    return row.score;
  }

  function dottedLearningScoreAt(r: PerformanceRecord, dimName: string): number | null {
    if (!r.dottedManagerId) return null;
    if (matrixEditDotted.value) {
      const row = learningForm.value.find((x) => x.name === dimName);
      if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
      return row.score;
    }
    const row = r.learningDottedManagerReview?.find(
      (x) => x.indicatorName === dimName || (x as ReviewItem & { name?: string }).name === dimName,
    );
    if (!row || typeof row.score !== "number" || Number.isNaN(row.score)) return null;
    return row.score;
  }

  function previewMergedLearningByName(dimName: string): number | null {
    const r = rec.value;
    if (!r) return null;
    if (!r.dottedManagerId) {
      return managerLearningScoreAt(r, dimName);
    }
    const [mw, dw] = liveMergeWeights.value;
    const ms = managerLearningScoreAt(r, dimName);
    const ds = dottedLearningScoreAt(r, dimName);
    return PS.computeMergedIndicatorScore(ms, ds, mw, dw);
  }

  function previewMergedCultureByName(dimensionName: string): number | null {
    const r = rec.value;
    if (!r) return null;
    if (!r.dottedManagerId) {
      return managerCultureScoreAt(r, dimensionName);
    }
    const [mw, dw] = liveMergeWeights.value;
    const ms = managerCultureScoreAt(r, dimensionName);
    const ds = dottedCultureScoreAt(r, dimensionName);
    return PS.computeMergedIndicatorScore(ms, ds, mw, dw);
  }

  const previewMergedCultureSum = computed((): number | null => {
    const r = rec.value;
    if (!r) return null;
    let t = 0;
    for (const def of recordCultureDimensions(r)) {
      const v = previewMergedCultureByName(def.name);
      if (v == null) return null;
      t += v;
    }
    return t;
  });

  const cultureDimensions = computed(() => recordCultureDimensions(rec.value));
  const learningDimensions = computed(() => rec.value?.learningDimensions ?? []);
  const scoringWeights = computed(() => rec.value?.scoringWeights ?? null);

  const previewMergedByIndex = computed(() => {
    const r = rec.value;
    return (index: number): number | null => {
      if (!r?.indicators?.length || !r.dottedManagerId) return null;
      const [mw, dw] = liveMergeWeights.value;
      const ms = managerIndicatorScoreAt(r, index);
      const ds = dottedIndicatorScoreAt(r, index);
      return PS.computeMergedIndicatorScore(ms, ds, mw, dw);
    };
  });

  const effectiveManagerPerfForMerge = computed((): ReviewItem[] | null => {
    const r = rec.value;
    if (!r?.indicators?.length) return null;
    if (matrixEditManager.value) {
      return PS.reviewItemsFromForm(r.indicators, formContent.value);
    }
    if (PS.isReviewCompleteForIndicators(r.managerReview, r.indicators)) {
      return r.managerReview ?? null;
    }
    return null;
  });

  const effectiveDottedPerfForMerge = computed((): ReviewItem[] | null => {
    const r = rec.value;
    if (!r?.indicators?.length || !r.dottedManagerId) return null;
    if (matrixEditDotted.value) {
      return PS.reviewItemsFromForm(r.indicators, formContent.value);
    }
    if (PS.isReviewCompleteForIndicators(r.dottedManagerReview, r.indicators)) {
      return r.dottedManagerReview ?? null;
    }
    return null;
  });

  const effectiveManagerLearningForMerge = computed((): ReviewItem[] | null => {
    const r = rec.value;
    const lInds = learningDimensions.value;
    if (!lInds.length) return null;
    if (matrixEditManager.value) {
      return learningForm.value.map((lf) => ({
        indicatorName: lf.name,
        score: lf.score as number,
        comment: lf.comment ?? "",
      }));
    }
    return r?.learningManagerReview ?? null;
  });

  const effectiveDottedLearningForMerge = computed((): ReviewItem[] | null => {
    const r = rec.value;
    const lInds = learningDimensions.value;
    if (!lInds.length || !r?.dottedManagerId) return null;
    if (matrixEditDotted.value) {
      return learningForm.value.map((lf) => ({
        indicatorName: lf.name,
        score: lf.score as number,
        comment: lf.comment ?? "",
      }));
    }
    return r?.learningDottedManagerReview ?? null;
  });

  const previewReviewMergedTotal = computed((): number | null => {
    const r = rec.value;
    if (!r?.indicators?.length) return null;
    const mgrEff = effectiveManagerPerfForMerge.value;
    const cultureMgr = matrixEditManager.value ? cultureForm.value : r.cultureManagerReview;
    const cultureDot = matrixEditDotted.value ? cultureForm.value : r.cultureDottedManagerReview;
    const [mw, dw] = liveMergeWeights.value;
    const sw = scoringWeights.value;
    const lInds = learningDimensions.value;
    const cDims = cultureDimensions.value;
    const lMgr = effectiveManagerLearningForMerge.value;
    const lDot = effectiveDottedLearningForMerge.value;
    if (!r.dottedManagerId) {
      return PS.computeMergedTotalScore(r.indicators, mgrEff, undefined, cultureMgr, cultureDot, false, mw, dw, sw, cDims, lMgr, null, lInds);
    }
    const dotEff = effectiveDottedPerfForMerge.value;
    if (dotEff == null) {
      return null;
    }
    return PS.computeMergedTotalScore(r.indicators, mgrEff, dotEff, cultureMgr, cultureDot, true, mw, dw, sw, cDims, lMgr, lDot, lInds);
  });

  const previewReviewMergedPerfOnlyTotal = computed((): number | null => {
    const r = rec.value;
    if (!r?.indicators?.length) return null;
    const mgrEff = effectiveManagerPerfForMerge.value;
    const [mw, dw] = liveMergeWeights.value;
    if (!r.dottedManagerId) {
      return PS.computeMergedPerfOnlyTotal(r.indicators, mgrEff, undefined, false, mw, dw);
    }
    const dotEff = effectiveDottedPerfForMerge.value;
    if (dotEff == null) {
      return null;
    }
    return PS.computeMergedPerfOnlyTotal(r.indicators, mgrEff, dotEff, true, mw, dw);
  });

  const matrixTemplateTotals = computed(() => ({
    self: matrixEditSelf.value ? previewEditingPerfOnlyTotal.value : readonlyPerfOnlyTotals.value.self,
    manager: matrixEditManager.value ? previewEditingPerfOnlyTotal.value : readonlyPerfOnlyTotals.value.manager,
    dotted: matrixEditDotted.value ? previewEditingPerfOnlyTotal.value : readonlyPerfOnlyTotals.value.dotted,
  }));

  return {
    PERFORMANCE_DETAIL_STEPS,
    rec,
    err,
    loading,
    savingDraft,
    submittingGoal,
    submittingReview,
    rejectingSubordinate,
    approvingGoal,
    calibrating,
    confirmingResult,
    templates,
    templateLoading,
    selecting,
    savingGoalIndicators,
    goalIndicatorDraft,
    goalSettings,
    formContent,
    cultureForm,
    learningForm,
    personalSummary,
    managerSummary,
    dottedManagerSummary,
    rejectReason,
    rejectOpen,
    managerRejectOpen,
    managerRejectReason,
    canRejectSubordinateReview,
    finalRejectOpen,
    finalReturnToStage,
    statusInfo,
    currentStepIndex,
    progressValue,
    indicators,
    goalPerfWeightSum,
    isEmployee,
    isManager,
    isDottedManager,
    canEdit,
    canSubmit,
    canGoalApprove,
    canFinalApprove,
    showScoresInFinalReview,
    showWeightedScores,
    showActionBar,
    canFinalCalibrationUser,
    formatScore,
    reviewItems,
    handleScoreChange,
    handleCommentChange,
    handleLearningScoreChange,
    handleLearningCommentChange,
    handleSaveDraft,
    handleSubmitGoal,
    handleApproveGoal,
    handleRejectSubordinateReview,
    handleSubmitReview,
    onSelectTemplate,
    ensureGoalTemplatesLoaded,
    persistGoalIndicators,
    addGoalIndicatorRow,
    removeGoalIndicatorRow,
    startCustomGoalIndicators,
    handleCalibrate,
    handleConfirmResult,
    reviewFormTitle,
    stepResponsibleLabel,
    personLabel,
    submitReviewButtonLabel,
    showUnifiedReviewMatrix,
    showEmployeeSubmittedSelfReadonly,
    reviewLine,
    cultureLine,
    previewEditingTotal,
    readonlyReviewTotals,
    matrixEditSelf,
    matrixEditManager,
    matrixEditDotted,
    previewMergedByIndex,
    previewMergedCultureByName,
    previewMergedCultureSum,
    previewMergedLearningByName,
    cultureDimensions,
    learningDimensions,
    scoringWeights,
    previewReviewMergedTotal,
    previewReviewMergedPerfOnlyTotal,
    matrixTemplateTotals,
  };
}
