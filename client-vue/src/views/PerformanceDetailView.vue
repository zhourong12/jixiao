<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import PerformanceDimensionSnapshotReadonly from "@/components/performance/PerformanceDimensionSnapshotReadonly.vue";
import PerformanceEmployeeSelfSnapshot from "@/components/performance/PerformanceEmployeeSelfSnapshot.vue";
import PerformanceScoringMatrix from "@/components/performance/PerformanceScoringMatrix.vue";
import { performanceDetailCopy as c } from "@/composables/performanceDetailCopy";
import { usePerformanceDetailPage } from "@/composables/usePerformanceDetailPage";
import { formatPeriodDisplay } from "@/utils/period";

const router = useRouter();
const {
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
  calibrateRollbackOpen,
  calibrateRollbackStage,
  calibrateRollbackReason,
  calibrateRollbackDeadline,
  calibrateRollbackDeadlineFieldLabel,
  openCalibrateRollbackDialog,
  CALIBRATION_ROLLBACK_OPTIONS,
  calibrateRollbackTargetLabel,
  startingSelfReview,
  canIssueSelfReview,
  canRollbackPlanAnchor,
  planRollbackAnchorLabel,
  planRollbackDeadlineFieldLabel,
  planRollbackOpen,
  planRollbackDeadline,
  rollingBackPlan,
  confirmingResult,
  templates,
  templateLoading,
  selecting,
  savingGoalIndicators,
  goalIndicatorDraft,
  goalSettings,
  formContent,
  dottedFormContent,
  cultureForm,
  dottedCultureForm,
  learningForm,
  dottedLearningForm,
  dualSupervisorCalibrationEdit,
  personalSummary,
  managerSummary,
  dottedManagerSummary,
  rejectReason,
  rejectOpen,
  managerRejectOpen,
  managerRejectReason,
  canRejectSubordinateReview,
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
  handleCalibrateRollbackConfirm,
  handleIssueSelfReview,
  openPlanRollbackDialog,
  handleRollbackPlanAnchorConfirm,
  handleConfirmResult,
  stepDeadlineLabel,
  reviewFormTitle,
  stepResponsibleLabel,
  submitReviewButtonLabel,
  showUnifiedReviewMatrix,
  showEmployeeSubmittedSelfReadonly,
  previewEditingTotal,
  previewManagerEditingTotal,
  previewDottedEditingTotal,
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
  displayMergedTotalScore,
  displayDetailTotalScore,
  previewReviewMergedPerfOnlyTotal,
  matrixTemplateTotals,
} = usePerformanceDetailPage();

/** 仅在有「待处理驳回」语义的状态展示；避免流程已前进后仍显示历史目标驳回文案 */
const showRejectionBanner = computed(() => {
  const r = rec.value;
  if (!r?.rejectionReason?.trim()) return false;
  return r.status === "goal_rejected" || r.status === "self_review";
});

const templatePickerOpen = ref(false);

async function openTemplatePicker() {
  templatePickerOpen.value = true;
  await ensureGoalTemplatesLoaded();
}

function closeTemplatePicker() {
  templatePickerOpen.value = false;
}

async function pickTemplate(templateId: string) {
  const ok = await onSelectTemplate(templateId);
  if (ok) closeTemplatePicker();
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-3">
        <button type="button" class="text-sm text-primary hover:underline" @click="router.back()">{{ c.back }}</button>
        <h1 class="text-xl font-semibold text-foreground">{{ c.title }}</h1>
      </div>
      <span
        v-if="statusInfo"
        class="inline-flex items-center rounded-full px-3 py-1 text-xs font-medium"
        :class="statusInfo.badgeClass"
      >
        {{ statusInfo.label }}
      </span>
    </div>

    <div v-if="loading" class="flex h-64 items-center justify-center text-muted-foreground">{{ c.loading }}</div>
    <div v-else-if="err" class="flex h-64 items-center justify-center text-destructive">{{ err }}</div>
    <div v-else-if="!rec" class="flex h-64 items-center justify-center text-muted-foreground">{{ c.notFound }}</div>

    <template v-else-if="rec && statusInfo">

      <section
        v-if="rec.status === 'issued' && isEmployee"
        class="rounded-md border border-[var(--warning)] bg-[var(--warning-bg)] p-6 shadow-sm"
      >
        <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h3 class="text-base font-semibold text-foreground">绩效结果确认</h3>
            <p class="mt-2 text-sm text-muted-foreground">
              您的绩效结果已由管理员下发，请查看下方评分详情后点击确认。
            </p>
          </div>
          <button
            type="button"
            class="ui-btn-primary min-h-11 shrink-0 px-6 text-base font-semibold shadow-sm"
            :disabled="confirmingResult"
            @click="handleConfirmResult"
          >
            {{ confirmingResult ? "提交中…" : "确认已收到绩效结果" }}
          </button>
        </div>
      </section>

      <section v-if="rec.status !== 'plan_execution'" class="rounded-md border border-border bg-card p-6 shadow-sm">
        <p class="text-sm text-muted-foreground">{{ statusInfo.description }}</p>
      </section>

      <section class="rounded-md border border-border bg-card p-6 shadow-sm">
        <h2 class="mb-4 text-base font-semibold">{{ c.basicInfo }}</h2>
        <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
          <div>
            <p class="text-sm text-muted-foreground">{{ c.period }}</p>
            <p class="mt-1 font-medium" :title="rec.period">{{ formatPeriodDisplay(rec.period) }}</p>
          </div>
          <div>
            <p class="text-sm text-muted-foreground">{{ c.employee }}</p>
            <UserDisplay
              class="mt-1"
              :value="{ user_id: rec.employeeId, name: rec.employeeName || rec.employeeId }"
            />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">{{ c.feishuSubject }}</p>
            <p class="mt-1 font-medium">
              {{ rec.feishuSubjectName || rec.feishuSubjectCode || "—" }}
            </p>
          </div>
          <div>
            <p class="text-sm text-muted-foreground">{{ c.totalScore }}</p>
            <p class="mt-1 text-lg font-medium text-primary">{{ formatScore(displayDetailTotalScore) }}</p>
            <p v-if="rec.scoreGrade" class="mt-1 text-sm text-muted-foreground">
              {{ c.scoreGrade }}：<span class="font-semibold text-foreground">{{ rec.scoreGrade }}</span>
            </p>
          </div>
        </div>
        <div v-if="showWeightedScores && rec.dottedManagerId" class="mt-4 grid grid-cols-2 gap-4 border-t border-border pt-4 md:grid-cols-4">
          <div>
            <p class="text-sm text-muted-foreground">{{ c.dottedWeighted }}</p>
            <p class="mt-1 text-lg font-medium">{{ formatScore(rec.dottedManagerWeightedTotal) }}</p>
          </div>
        </div>
      </section>

      <section class="ui-card">
        <h2 class="mb-4 text-base font-semibold">{{ c.flow }}</h2>
        <div class="space-y-5">
          <div class="h-1.5 overflow-hidden rounded-full bg-muted">
            <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${progressValue}%` }" />
          </div>
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-8">
            <div
              v-for="(step, index) in PERFORMANCE_DETAIL_STEPS"
              :key="step.key"
              class="flex min-w-0 flex-col items-center gap-2 rounded-md border px-2 py-3 text-center"
              :class="
                index === currentStepIndex
                  ? 'border-primary bg-primary/5 text-primary'
                  : index < currentStepIndex
                    ? 'border-border bg-card text-foreground'
                    : 'border-border bg-background text-muted-foreground'
              "
            >
              <div
                class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold"
                :class="
                  index === currentStepIndex
                    ? 'bg-primary text-primary-foreground'
                    : index < currentStepIndex
                      ? 'bg-primary/15 text-primary'
                      : 'bg-muted text-muted-foreground'
                "
              >
                {{ index + 1 }}
              </div>
              <p class="text-sm font-medium leading-tight">{{ step.label }}</p>
              <p class="line-clamp-2 text-xs leading-snug text-muted-foreground">
                {{ stepResponsibleLabel(step.key) }}
              </p>
              <p v-if="stepDeadlineLabel(step.key)" class="text-xs text-primary/80">
                截止 {{ stepDeadlineLabel(step.key) }}
              </p>
            </div>
          </div>
        </div>
      </section>

      <section
        v-if="rec.status === 'plan_execution' && isEmployee"
        class="rounded-md border border-border bg-card p-6 shadow-sm"
      >
        <p class="text-sm text-muted-foreground">
          目标已审核通过，当前处于计划执行阶段。请等待创建该绩效的同事下发「员工自评」后再填写自评内容。
        </p>
      </section>

      <section v-if="showRejectionBanner" class="rounded-md border border-destructive/30 bg-destructive/5 p-6 shadow-sm">
        <h2 class="mb-2 text-base font-semibold text-destructive">{{ c.rejection }}</h2>
        <p class="text-sm">{{ rec.rejectionReason }}</p>
      </section>

      <!-- 目标设定（员工填写阶段，含模板选择） -->
      <section
        v-if="['goal_setting', 'goal_rejected'].includes(rec.status)"
        class="space-y-6"
      >
        <div class="rounded-md border border-border bg-card p-6 shadow-sm">
          <h2 class="mb-4 text-base font-semibold">{{ c.goalSetting }}</h2>

          <!-- 评分方案权重展示（占比为 0 的类型不展示） -->
          <div
            v-if="
              scoringWeights &&
              (scoringWeights.performance > 0 || scoringWeights.culture > 0 || scoringWeights.learning > 0)
            "
            class="mb-6 flex flex-wrap gap-3 text-sm"
          >
            <span v-if="scoringWeights.performance > 0" class="rounded-full bg-accent px-3 py-1">
              绩效 {{ scoringWeights.performance }}%
            </span>
            <span v-if="scoringWeights.culture > 0" class="rounded-full bg-accent px-3 py-1">
              文化价值观 {{ scoringWeights.culture }}%
            </span>
            <span v-if="scoringWeights.learning > 0" class="rounded-full bg-accent px-3 py-1">
              学习与成长 {{ scoringWeights.learning }}%
            </span>
          </div>

          <!-- 绩效指标（置于文化价值观之上） -->
          <h3 class="mb-3 text-sm font-semibold text-muted-foreground">绩效指标</h3>

          <template v-if="isEmployee && indicators.length === 0">
            <p class="mb-4 text-sm text-muted-foreground">请从模板库选择绩效模板，或使用自定义指标。</p>
            <div class="flex flex-wrap gap-2">
              <button
                type="button"
                class="rounded-md border border-border bg-card px-4 py-2 text-sm hover:bg-accent disabled:opacity-50"
                :disabled="selecting || savingGoalIndicators || templateLoading"
                @click="openTemplatePicker"
              >
                从模板库选择
              </button>
              <button
                type="button"
                class="rounded-md border border-primary bg-primary/10 px-4 py-2 text-sm text-primary hover:bg-primary/20 disabled:opacity-50"
                :disabled="selecting || savingGoalIndicators || templateLoading"
                @click="startCustomGoalIndicators"
              >
                使用自定义指标
              </button>
            </div>
          </template>

          <template v-else-if="isEmployee && goalIndicatorDraft.length">
            <p class="mb-3 text-xs text-muted-foreground">
              各指标「权重(%)」合计须等于评分方案中「绩效」的占比，可直接增删改并保存。
            </p>
            <p v-if="scoringWeights && scoringWeights.performance > 0" class="mb-2 text-xs text-muted-foreground">
              当前合计：{{ goalPerfWeightSum }}%（须等于绩效占比 {{ scoringWeights.performance }}%；本方案：绩效
              {{ scoringWeights.performance }}% / 文化价值观 {{ scoringWeights.culture }}% / 学习与成长
              {{ scoringWeights.learning }}%）
            </p>
            <p v-else class="mb-2 text-xs text-muted-foreground">当前合计：{{ goalPerfWeightSum }}%（须为 100%）</p>
            <div class="mb-3 flex flex-wrap gap-2">
              <button
                type="button"
                class="rounded-md border border-border px-3 py-1.5 text-sm"
                :disabled="savingGoalIndicators"
                @click="addGoalIndicatorRow"
              >
                添加指标
              </button>
              <button
                type="button"
                class="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
                :disabled="savingGoalIndicators || submittingGoal"
                @click="persistGoalIndicators"
              >
                {{ savingGoalIndicators ? "保存中…" : "保存指标结构" }}
              </button>
            </div>
            <div class="space-y-4">
              <div
                v-for="(row, idx) in goalIndicatorDraft"
                :key="idx"
                class="rounded-md border border-border p-4"
              >
                <div class="mb-3 flex flex-wrap items-end gap-3">
                  <label class="flex min-w-[8rem] flex-1 flex-col gap-1 text-xs text-muted-foreground">
                    指标名称
                    <input v-model="row.name" type="text" class="rounded-md border border-border px-2 py-1.5 text-sm" />
                  </label>
                  <button
                    v-if="goalIndicatorDraft.length > 1"
                    type="button"
                    class="ml-auto text-xs text-destructive hover:underline"
                    :disabled="savingGoalIndicators"
                    @click="removeGoalIndicatorRow(idx)"
                  >
                    删除此行
                  </button>
                </div>
                <label class="mb-2 flex flex-col gap-1 text-xs text-muted-foreground">
                  权重（%）
                  <input
                    v-model.number="row.weight"
                    type="number"
                    min="0"
                    step="0.1"
                    class="rounded-md border border-border px-2 py-1.5 text-sm"
                  />
                </label>
                <label class="flex flex-col gap-1 text-xs text-muted-foreground">
                  衡量标准
                  <textarea
                    v-model="row.criteria"
                    rows="5"
                    class="mt-1 w-full resize-y rounded-md border border-border px-3 py-2 text-sm"
                    placeholder="填写衡量标准（评分依据）"
                  />
                </label>
              </div>
            </div>
          </template>

          <template v-else-if="indicators.length > 0 && (!isEmployee || goalIndicatorDraft.length === 0)">
            <div class="space-y-4">
              <div v-for="(indicator, idx) in indicators" :key="indicator.name" class="rounded-md border border-border p-4">
                <div class="flex items-center justify-between gap-3">
                  <div>
                    <h3 class="font-medium">{{ indicator.name }}</h3>
                    <p v-if="indicator.description" class="mt-1 text-sm text-muted-foreground">{{ indicator.description }}</p>
                  </div>
                  <div class="flex flex-wrap items-center gap-2">
                    <span v-if="indicator.maxScore" class="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                      最高分 {{ indicator.maxScore }}
                    </span>
                    <span class="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                      权重 {{ Number(indicator.weight).toFixed(1) }}%
                    </span>
                  </div>
                </div>
                <div class="mt-3">
                  <label class="text-xs text-muted-foreground">衡量标准</label>
                  <textarea
                    v-if="isEmployee"
                    v-model="goalSettings[idx]!.criteria"
                    rows="7"
                    class="mt-1 min-h-[10.5rem] w-full resize-y rounded-md border border-border px-3 py-2 text-sm"
                    placeholder="填写衡量标准（评分依据）"
                  />
                  <p v-else class="mt-1 whitespace-pre-wrap text-sm">{{ goalSettings[idx]?.criteria || indicator.criteria || '-' }}</p>
                </div>
              </div>
            </div>
          </template>

          <template v-else>
            <p class="text-sm text-muted-foreground">暂无绩效指标</p>
          </template>

          <PerformanceDimensionSnapshotReadonly
            :culture-dimensions="cultureDimensions"
            :learning-dimensions="learningDimensions"
            :scoring-weights="scoringWeights"
            :culture-self-review="rec.cultureSelfReview"
            :learning-self-review="rec.learningSelfReview"
            :format-score="formatScore"
          />
        </div>
      </section>

      <!-- 目标审核（待上级审核，只读展示 + 审核按钮） -->
      <section
        v-if="rec.status === 'goal_pending_review'"
        class="rounded-md border border-border bg-card p-6 shadow-sm"
      >
        <h2 class="mb-4 text-base font-semibold">{{ c.goalSubmitted }}</h2>
        <div class="space-y-4">
          <div v-for="(indicator, idx) in indicators" :key="indicator.name" class="rounded-md border border-border p-4">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h3 class="font-medium">{{ indicator.name }}</h3>
                <p v-if="indicator.description" class="mt-1 text-sm text-muted-foreground">{{ indicator.description }}</p>
              </div>
              <div class="flex items-center gap-3">
                <span v-if="indicator.maxScore" class="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                  最高分 {{ indicator.maxScore }}
                </span>
                <span class="rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground">
                  {{ c.weight }} {{ indicator.weight }}%
                </span>
              </div>
            </div>
            <div class="mt-3">
              <label class="text-xs text-muted-foreground">衡量标准</label>
              <p class="mt-1 whitespace-pre-wrap text-sm">{{ goalSettings[idx]?.criteria || indicator.criteria || '-' }}</p>
            </div>
          </div>
        </div>
        <PerformanceDimensionSnapshotReadonly
          :culture-dimensions="cultureDimensions"
          :learning-dimensions="learningDimensions"
          :scoring-weights="scoringWeights"
          :culture-self-review="rec.cultureSelfReview"
          :learning-self-review="rec.learningSelfReview"
          :format-score="formatScore"
        />
      </section>

      <div v-if="rec.status === 'goal_pending_review' && canGoalApprove" class="flex justify-end gap-3">
        <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="rejectOpen = true">
          {{ c.reject }}
        </button>
        <button
          type="button"
          class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
          :disabled="approvingGoal"
          @click="handleApproveGoal(true)"
        >
          {{ approvingGoal ? "处理中…" : c.approve }}
        </button>
      </div>

      <PerformanceScoringMatrix
        v-if="showUnifiedReviewMatrix"
        v-model:form-content="formContent"
        v-model:dotted-form-content="dottedFormContent"
        v-model:culture-form="cultureForm"
        v-model:dotted-culture-form="dottedCultureForm"
        v-model:learning-form="learningForm"
        v-model:dotted-learning-form="dottedLearningForm"
        v-model:personal-summary="personalSummary"
        v-model:manager-summary="managerSummary"
        v-model:dotted-manager-summary="dottedManagerSummary"
        :rec="rec"
        :indicators="indicators"
        :culture-dimensions="cultureDimensions"
        :learning-dimensions="learningDimensions"
        :scoring-weights="scoringWeights"
        :matrix-edit-self="matrixEditSelf"
        :matrix-edit-manager="matrixEditManager"
        :matrix-edit-dotted="matrixEditDotted"
        :dual-supervisor-edit="dualSupervisorCalibrationEdit"
        :readonly-review-totals="readonlyReviewTotals"
        :preview-editing-total="previewEditingTotal"
        :preview-manager-editing-total="previewManagerEditingTotal"
        :preview-dotted-editing-total="previewDottedEditingTotal"
        :preview-merged-by-index="previewMergedByIndex"
        :preview-merged-culture-by-name="previewMergedCultureByName"
        :preview-merged-culture-sum="previewMergedCultureSum"
        :preview-merged-learning-by-name="previewMergedLearningByName"
        :preview-review-merged-total="previewReviewMergedTotal"
        :display-merged-total-score="displayMergedTotalScore"
        :preview-review-merged-perf-only-total="previewReviewMergedPerfOnlyTotal"
        :matrix-template-totals="matrixTemplateTotals"
        :review-form-title="reviewFormTitle()"
        :format-score="formatScore"
      />

      <PerformanceEmployeeSelfSnapshot
        v-if="
          isEmployee &&
          !showUnifiedReviewMatrix &&
          showEmployeeSubmittedSelfReadonly &&
          ['manager_review', 'dual_manager_review', 'dotted_manager_review', 'final_review', 'completed', 'issued'].includes(
            rec.status,
          )
        "
        :indicators="indicators"
        :self-review="rec.selfReview"
        :personal-summary="rec.personalSummary"
        :format-score="formatScore"
      />

      <template
        v-if="
          isEmployee &&
          ['manager_review', 'dual_manager_review', 'dotted_manager_review', 'final_review'].includes(rec.status)
        "
      >
        <section
          v-if="['manager_review', 'dual_manager_review', 'dotted_manager_review'].includes(rec.status)"
          class="rounded-md border border-border bg-card p-6 shadow-sm"
        >
          <p class="text-sm text-muted-foreground">
            {{
              rec.status === "manager_review"
                ? c.waitManagerReview
                : rec.status === "dual_manager_review"
                  ? c.waitDualReview
                  : c.waitDottedReview
            }}
          </p>
          <PerformanceDimensionSnapshotReadonly
            v-if="!showUnifiedReviewMatrix"
            :culture-dimensions="cultureDimensions"
            :learning-dimensions="learningDimensions"
            :scoring-weights="scoringWeights"
            :culture-self-review="rec.cultureSelfReview"
            :learning-self-review="rec.learningSelfReview"
            :format-score="formatScore"
          />
        </section>
        <section
          v-if="rec.status === 'final_review' && !showScoresInFinalReview"
          class="rounded-md border border-border bg-card p-6 shadow-sm"
        >
          <p class="text-sm text-muted-foreground">{{ c.waitCalibration }}</p>
          <PerformanceDimensionSnapshotReadonly
            v-if="!showUnifiedReviewMatrix"
            :culture-dimensions="cultureDimensions"
            :learning-dimensions="learningDimensions"
            :scoring-weights="scoringWeights"
            :culture-self-review="rec.cultureSelfReview"
            :learning-self-review="rec.learningSelfReview"
            :format-score="formatScore"
          />
        </section>
      </template>

      <section
        v-if="
          rec.status === 'final_review' &&
          !showScoresInFinalReview &&
          !isEmployee &&
          !(isManager || (isDottedManager && rec.dottedManagerId))
        "
        class="rounded-md border border-border bg-card p-6 shadow-sm"
      >
        <p class="text-sm text-muted-foreground">{{ c.waitCalibration }}</p>
      </section>

      <template v-if="rec.status === 'completed'">
        <section class="rounded-md border border-border bg-card p-6 shadow-sm">
          <p class="text-sm text-muted-foreground">{{ c.completed }}</p>
        </section>
      </template>

      <section v-if="rec.status === 'final_review' && canFinalCalibrationUser" class="rounded-md border border-primary/30 bg-card p-6 shadow-sm">
        <h2 class="mb-4 text-base font-semibold">{{ c.calibrateTitle }}</h2>
        <div class="space-y-4">
          <p class="text-sm text-muted-foreground">
            {{ c.currentTotalLabel }}:
            <span class="text-lg font-semibold text-primary">{{ formatScore(displayDetailTotalScore) }}</span>
          </p>
        </div>
      </section>

      <div
        v-if="showActionBar"
        class="sticky bottom-0 z-10 flex flex-wrap items-center justify-end gap-2 rounded-lg border border-border bg-card p-3 shadow-sm md:static md:gap-3 md:p-4"
      >
        <button
          v-if="canEdit && rec.status !== 'goal_pending_review'"
          type="button"
          class="ui-btn-outline"
          :disabled="savingDraft"
          @click="handleSaveDraft"
        >
          {{ savingDraft ? "保存中…" : c.saveDraft }}
        </button>
        <button
          v-if="canRejectSubordinateReview"
          type="button"
          class="ui-btn-outline min-h-11"
          :disabled="rejectingSubordinate"
          @click="managerRejectOpen = true"
        >
          {{ c.reject }}
        </button>
        <button
          v-if="canSubmit && rec.status !== 'goal_setting' && rec.status !== 'goal_rejected'"
          type="button"
          class="ui-btn-primary min-h-11 min-w-[7.5rem] px-6 text-base font-semibold shadow-sm"
          :disabled="submittingReview"
          @click="handleSubmitReview"
        >
          {{ submittingReview ? "提交中…" : submitReviewButtonLabel }}
        </button>
        <button
          v-if="(rec.status === 'goal_setting' || rec.status === 'goal_rejected') && isEmployee && indicators.length > 0"
          type="button"
          class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
          :disabled="submittingGoal"
          @click="handleSubmitGoal"
        >
          {{ submittingGoal ? "提交中…" : c.submitGoal }}
        </button>
        <button
          v-if="canFinalApprove"
          type="button"
          class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
          :disabled="calibrating"
          @click="handleCalibrate()"
        >
          {{ calibrating ? "处理中…" : c.calibratePass }}
        </button>
        <button
          v-if="canIssueSelfReview"
          type="button"
          class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
          :disabled="startingSelfReview"
          @click="handleIssueSelfReview"
        >
          {{ startingSelfReview ? "处理中…" : "下发员工自评" }}
        </button>
        <button
          v-if="canRollbackPlanAnchor"
          type="button"
          class="ui-btn-outline"
          :disabled="rollingBackPlan"
          @click="openPlanRollbackDialog"
        >
          {{ rollingBackPlan ? "处理中…" : `回退恢复（${planRollbackAnchorLabel}）` }}
        </button>
        <button
          v-if="canFinalApprove"
          type="button"
          class="ui-btn-outline"
          :disabled="calibrating"
          @click="openCalibrateRollbackDialog"
        >
          {{ c.calibrateReject }}
        </button>
      </div>
    </template>

    <div
      v-if="planRollbackOpen"
      class="ui-dialog-backdrop"
      @click.self="planRollbackOpen = false"
    >
      <div class="w-full max-w-md space-y-4 rounded-md border border-border bg-card p-4 shadow-sm md:p-6">
        <h2 class="text-lg font-semibold text-foreground">回退恢复确认</h2>
        <p class="text-sm text-muted-foreground">
          将流程回退至「<span class="font-medium text-foreground">{{ planRollbackAnchorLabel }}</span>」，并更新该节点截止时间。
        </p>
        <div>
          <label class="ui-label block text-sm font-medium">{{ planRollbackDeadlineFieldLabel }}</label>
          <input
            v-model="planRollbackDeadline"
            type="date"
            class="mt-1 w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="planRollbackOpen = false">
            取消
          </button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
            :disabled="rollingBackPlan"
            @click="handleRollbackPlanAnchorConfirm"
          >
            {{ rollingBackPlan ? "处理中…" : "确认回退" }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="calibrateRollbackOpen"
      class="ui-dialog-backdrop"
      @click.self="calibrateRollbackOpen = false"
    >
      <div class="w-full max-w-md space-y-4 rounded-md border border-border bg-card p-4 shadow-sm md:p-6">
        <h2 class="text-lg font-semibold text-foreground">校准回退确认</h2>
        <p class="text-sm text-muted-foreground">
          确认将流程回退至「<span class="font-medium text-foreground">{{ calibrateRollbackTargetLabel }}</span>」？回退后需重新处理该节点，并更新该节点截止时间。
        </p>
        <div>
          <label class="ui-label block text-sm font-medium">回退到</label>
          <select v-model="calibrateRollbackStage" class="mt-1 w-full rounded-md border border-border bg-card px-3 py-2 text-sm">
            <option v-for="opt in CALIBRATION_ROLLBACK_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>
        <div>
          <label class="ui-label block text-sm font-medium">{{ calibrateRollbackDeadlineFieldLabel }}</label>
          <input
            v-model="calibrateRollbackDeadline"
            type="date"
            class="mt-1 w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label class="ui-label block text-sm font-medium">回退说明（选填）</label>
          <textarea
            v-model="calibrateRollbackReason"
            class="mt-1 min-h-[80px] w-full rounded-md border border-border px-3 py-2 text-sm"
            placeholder="请填写回退原因"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="calibrateRollbackOpen = false">
            取消
          </button>
          <button
            type="button"
            class="rounded-md bg-destructive px-4 py-2 text-sm text-white"
            :disabled="calibrating"
            @click="handleCalibrateRollbackConfirm"
          >
            {{ calibrating ? "处理中…" : "确认回退" }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="rejectOpen" class="ui-dialog-backdrop" @click.self="rejectOpen = false">
      <div class="w-full max-w-md space-y-4 rounded-md border border-border bg-card p-4 shadow-sm md:p-6">
        <h2 class="text-lg font-semibold text-foreground">{{ c.rejectGoalTitle }}</h2>
        <p class="text-sm text-muted-foreground">{{ c.rejectGoalDesc }}</p>
        <div>
          <label class="ui-label block text-sm font-medium">{{ c.rejectReasonLabel }}</label>
          <textarea
            v-model="rejectReason"
            class="mt-1 min-h-[100px] w-full rounded-md border border-border px-3 py-2 text-sm"
            :placeholder="c.rejectReasonPlaceholder"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="rejectOpen = false">{{ c.cancel }}</button>
          <button
            type="button"
            class="rounded-md bg-destructive px-4 py-2 text-sm text-white"
            :disabled="approvingGoal"
            @click="handleApproveGoal(false)"
          >
            {{ approvingGoal ? "处理中…" : c.confirmReject }}
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="managerRejectOpen"
      class="ui-dialog-backdrop"
      @click.self="managerRejectOpen = false"
    >
      <div class="w-full max-w-md space-y-4 rounded-md border border-border bg-card p-4 shadow-sm md:p-6">
        <h2 class="text-lg font-semibold text-foreground">{{ c.rejectSelfReviewTitle }}</h2>
        <p class="text-sm text-muted-foreground">{{ c.rejectSelfReviewDesc }}</p>
        <div>
          <label class="ui-label block text-sm font-medium">{{ c.rejectReasonLabel }}</label>
          <textarea
            v-model="managerRejectReason"
            class="mt-1 min-h-[100px] w-full rounded-md border border-border px-3 py-2 text-sm"
            :placeholder="c.rejectReasonPlaceholder"
          />
        </div>
        <div class="flex justify-end gap-3 pt-2">
          <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="managerRejectOpen = false">{{ c.cancel }}</button>
          <button
            type="button"
            class="rounded-md bg-destructive px-4 py-2 text-sm text-white"
            :disabled="rejectingSubordinate"
            @click="handleRejectSubordinateReview"
          >
            {{ rejectingSubordinate ? "处理中…" : c.confirmReject }}
          </button>
        </div>
      </div>
    </div>


    <!-- 已完成 -->
    <section v-if="rec && rec.status === 'completed'" class="rounded-md border border-[var(--success)] bg-[var(--success-bg)] p-6 shadow-sm">
      <p class="text-sm font-medium text-[var(--success)]">✓ 绩效评估已完成，员工已确认结果。</p>
    </section>

    <!-- 模板库选择 -->
    <div
      v-if="templatePickerOpen"
      class="ui-dialog-backdrop"
      @click.self="closeTemplatePicker"
    >
      <div class="ui-card flex max-h-[min(85vh,720px)] w-full max-w-2xl flex-col p-4 md:p-6">
        <div class="mb-4 flex items-start justify-between gap-3">
          <div>
            <h2 class="text-lg font-semibold">选择绩效模板</h2>
            <p class="mt-1 text-sm text-muted-foreground">从模板库中选择一个模板作为绩效指标结构</p>
          </div>
          <button type="button" class="ui-btn-ghost ui-btn-sm shrink-0" @click="closeTemplatePicker">关闭</button>
        </div>
        <p v-if="templateLoading" class="py-12 text-center text-sm text-muted-foreground">{{ c.templateLoading }}</p>
        <p v-else-if="templates.length === 0" class="py-12 text-center text-sm text-muted-foreground">{{ c.noTemplate }}</p>
        <div v-else class="min-h-0 flex-1 overflow-y-auto">
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <button
              v-for="t in templates"
              :key="t.id"
              type="button"
              class="rounded-md border-2 border-border bg-card p-4 text-left transition-colors hover:border-primary disabled:opacity-50"
              :disabled="selecting"
              @click="pickTemplate(t.id)"
            >
              <p class="text-base font-semibold">{{ t.name }}</p>
              <p class="mt-2 text-sm text-muted-foreground">{{ t.position }}</p>
              <p class="mt-3 text-xs text-muted-foreground">{{ t.indicatorCount }} {{ c.indicators }}</p>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
