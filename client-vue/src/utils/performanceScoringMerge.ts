import type { CultureDimensionDef, CultureReviewItem, PerformanceIndicator, ReviewItem } from "@/types/api.interface";

export interface ScoringWeights {
  performance: number;
  culture: number;
  learning: number;
}

export function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

/** 分项加权平均后再乘方案占比（绩效/学习总分行展示用） */
export function applySchemeWeightPortion(internalRate: number | null, portionPct: number): number | null {
  if (internalRate === null) return null;
  if (portionPct > 0) {
    return round2(internalRate * (portionPct / 100));
  }
  return internalRate;
}

/** 学习类 JSON 常用 name，绩效类用 indicatorName */
export function reviewItemDimensionKey(it: ReviewItem & { name?: string }): string {
  return it.indicatorName || it.name || "";
}

/** 与后端 PerformanceService.perfWeightFactorFromIndicators 一致 */
export function perfWeightFactorFromIndicators(inds: PerformanceIndicator[]): number {
  const tw = inds.reduce((s, i) => s + (Number(i.weight) || 0), 0);
  return tw > 0 ? tw / 100 : 0.8;
}

/** 文化得分率 = 各维度得分之和 / 各维度满分之和 × 100 */
export function cultureScoreRate(
  culture: CultureReviewItem[] | undefined,
  dims: CultureDimensionDef[] | undefined,
): number | null {
  if (!culture?.length || !dims?.length) return null;
  const maxSum = dims.reduce((s, d) => s + (Number(d.maxScore) || 0), 0);
  if (maxSum <= 0) return null;
  const scored = sumCultureScores(culture);
  return (scored / maxSum) * 100;
}

/** 学习得分率 = calcWeightedScore（加权平均，百分制） */
export function learningScoreRate(
  review: ReviewItem[] | null | undefined,
  inds: PerformanceIndicator[] | undefined,
): number | null {
  if (!review?.length || !inds?.length) return null;
  return calcWeightedScore(review, inds);
}

/** 使用方案占比公式计算单评审人总分 */
export function computeSingleReviewerTotalWithWeights(
  perfRate: number | null,
  cultureRate: number | null,
  learningRate: number | null,
  sw: ScoringWeights,
): number | null {
  let total = 0;
  if (sw.performance > 0) {
    if (perfRate === null) return null;
    total += perfRate * (sw.performance / 100);
  }
  if (sw.culture > 0) {
    if (cultureRate === null) return null;
    total += cultureRate * (sw.culture / 100);
  }
  if (sw.learning > 0) {
    if (learningRate === null) return null;
    total += learningRate * (sw.learning / 100);
  }
  return round2(total);
}

/** 与后端 calcWeightedScore 一致（按模板指标权重加权平均） */
export function calcWeightedScore(review: ReviewItem[] | null | undefined, inds: PerformanceIndicator[]): number | null {
  if (!review?.length || !inds.length) return null;
  let totalWeightedScore = 0;
  let totalWeight = 0;
  for (const item of review) {
    const name = reviewItemDimensionKey(item as ReviewItem & { name?: string });
    if (!name) return null;
    const ind = inds.find((x) => x.name === name);
    const w = ind ? Number(ind.weight) || 0 : 0;
    const score = item.score;
    if (typeof score !== "number" || Number.isNaN(score)) return null;
    totalWeightedScore += score * w;
    totalWeight += w;
  }
  if (totalWeight > 0) {
    return totalWeightedScore / totalWeight;
  }
  const sum = review.reduce((a, it) => a + (typeof it.score === "number" && !Number.isNaN(it.score) ? it.score : 0), 0);
  return review.length > 0 ? sum / review.length : null;
}

export function sumCultureScores(culture: CultureReviewItem[] | undefined): number {
  if (!culture?.length) return 0;
  let t = 0;
  for (const it of culture) {
    if (typeof it.score === "number" && !Number.isNaN(it.score)) t += it.score;
  }
  return t;
}

export function reviewItemsFromForm(
  inds: PerformanceIndicator[],
  form: { indicatorName: string; score?: number; comment?: string }[],
): ReviewItem[] | null {
  if (form.length < inds.length) return null;
  const out: ReviewItem[] = [];
  for (let i = 0; i < inds.length; i++) {
    const ind = inds[i]!;
    const row = form[i];
    if (!row || row.indicatorName !== ind.name) return null;
    const s = row.score;
    if (s === undefined || s === null || Number.isNaN(s)) return null;
    out.push({ indicatorName: ind.name, score: s, comment: row.comment ?? "" });
  }
  return out;
}

export function isReviewCompleteForIndicators(review: ReviewItem[] | undefined, inds: PerformanceIndicator[]): boolean {
  if (!review?.length || !inds.length) return false;
  for (const ind of inds) {
    const ri = review.find((x) => reviewItemDimensionKey(x as ReviewItem & { name?: string }) === ind.name);
    if (ri == null || typeof ri.score !== "number" || Number.isNaN(ri.score)) return false;
  }
  return true;
}

export function normalizeRoleWeights(mw?: number, dw?: number): [number, number] {
  if (mw != null && dw != null && !Number.isNaN(mw) && !Number.isNaN(dw)) {
    return [mw, dw];
  }
  return [0.5, 0.5];
}

/** 与后端 computeTotalScore（含双上级文化加权）一致 */
export function computeMergedTotalScore(
  inds: PerformanceIndicator[],
  managerReview: ReviewItem[] | null | undefined,
  dottedReview: ReviewItem[] | null | undefined,
  cultureManager: CultureReviewItem[] | undefined,
  cultureDotted: CultureReviewItem[] | undefined,
  hasDottedManager: boolean,
  managerWeight: number,
  dottedWeight: number,
  scoringWeights?: ScoringWeights | null,
  cultureDims?: CultureDimensionDef[],
  learningManager?: ReviewItem[] | null,
  learningDotted?: ReviewItem[] | null,
  learningInds?: PerformanceIndicator[],
): number | null {
  if (scoringWeights) {
    return computeMergedTotalWithScheme(
      inds, managerReview, dottedReview,
      cultureManager, cultureDotted,
      hasDottedManager, managerWeight, dottedWeight,
      scoringWeights, cultureDims,
      learningManager, learningDotted, learningInds,
    );
  }
  const pf = perfWeightFactorFromIndicators(inds);
  const mScore = calcWeightedScore(managerReview, inds);
  if (mScore === null) return null;
  const mCulture = sumCultureScores(cultureManager);
  if (!hasDottedManager || dottedReview == null || dottedReview.length === 0) {
    return round2(mScore * pf + mCulture);
  }
  const dScore = calcWeightedScore(dottedReview, inds);
  if (dScore === null) return null;
  const dCulture = sumCultureScores(cultureDotted);
  const perfScore = mScore * managerWeight + dScore * dottedWeight;
  const cultureScore = mCulture * managerWeight + dCulture * dottedWeight;
  return round2(perfScore * pf + cultureScore);
}

function computeMergedTotalWithScheme(
  inds: PerformanceIndicator[],
  managerReview: ReviewItem[] | null | undefined,
  dottedReview: ReviewItem[] | null | undefined,
  cultureManager: CultureReviewItem[] | undefined,
  cultureDotted: CultureReviewItem[] | undefined,
  hasDottedManager: boolean,
  managerWeight: number,
  dottedWeight: number,
  sw: ScoringWeights,
  cultureDims?: CultureDimensionDef[],
  learningManager?: ReviewItem[] | null,
  learningDotted?: ReviewItem[] | null,
  learningInds?: PerformanceIndicator[],
): number | null {
  const useDotted = hasDottedManager && dottedReview != null && dottedReview.length > 0;

  let perfRate: number | null = null;
  if (sw.performance > 0) {
    const mPerf = calcWeightedScore(managerReview, inds);
    if (mPerf === null) return null;
    if (useDotted) {
      const dPerf = calcWeightedScore(dottedReview, inds);
      if (dPerf === null) return null;
      perfRate = mPerf * managerWeight + dPerf * dottedWeight;
    } else {
      perfRate = mPerf;
    }
  }

  let cRate: number | null = null;
  if (sw.culture > 0) {
    const mCR = cultureScoreRate(cultureManager, cultureDims);
    if (mCR === null) return null;
    if (useDotted) {
      const dCR = cultureScoreRate(cultureDotted, cultureDims);
      if (dCR === null) return null;
      cRate = mCR * managerWeight + dCR * dottedWeight;
    } else {
      cRate = mCR;
    }
  }

  let lRate: number | null = null;
  if (sw.learning > 0) {
    const mLR = learningScoreRate(learningManager, learningInds);
    if (mLR === null) return null;
    if (useDotted) {
      const dLR = learningScoreRate(learningDotted, learningInds);
      if (dLR === null) return null;
      lRate = mLR * managerWeight + dLR * dottedWeight;
    } else {
      lRate = mLR;
    }
  }

  return computeSingleReviewerTotalWithWeights(perfRate, cRate, lRate, sw);
}

/** 与 computeMergedTotalScore 相同口径，但不含文化价值观和学习（用于模板内总分行「结果」列） */
export function computeMergedPerfOnlyTotal(
  inds: PerformanceIndicator[],
  managerReview: ReviewItem[] | null | undefined,
  dottedReview: ReviewItem[] | null | undefined,
  hasDottedManager: boolean,
  managerWeight: number,
  dottedWeight: number,
): number | null {
  const mScore = calcWeightedScore(managerReview, inds);
  if (mScore === null) return null;
  if (!hasDottedManager || dottedReview == null || dottedReview.length === 0) {
    return round2(mScore);
  }
  const dScore = calcWeightedScore(dottedReview, inds);
  if (dScore === null) return null;
  return round2(mScore * managerWeight + dScore * dottedWeight);
}

export function computeMergedIndicatorScore(
  managerScore: number | null,
  dottedScore: number | null,
  managerWeight: number,
  dottedWeight: number,
): number | null {
  if (managerScore == null || dottedScore == null) return null;
  return round2(managerScore * managerWeight + dottedScore * dottedWeight);
}

/** 与后端 PerformanceService.scoreGradeFromTotal 档位一致 */
export function scoreGradeFromTotal(totalScore: number): "S" | "A" | "B" | "C" {
  if (!Number.isFinite(totalScore)) return "C";
  if (totalScore > 95) return "S";
  if (totalScore > 90) return "A";
  if (totalScore > 70) return "B";
  return "C";
}
