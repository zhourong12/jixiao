import type { PerformanceStatus } from "@/types/api.interface";

export type PerformanceStatusTone = "info" | "success" | "warning" | "danger" | "neutral" | "primary";

/** 已弃用：流程已合并到 goal_setting，筛选项不展示 */
export const DEPRECATED_PERFORMANCE_STATUSES: PerformanceStatus[] = ["template_selection"];

export function normalizePerformanceStatus(status: PerformanceStatus | string): PerformanceStatus {
  if (status === "template_selection") return "goal_setting";
  return status as PerformanceStatus;
}

export const PERFORMANCE_STATUS_LABELS: Record<PerformanceStatus, string> = {
  template_selection: "目标设定中",
  goal_setting: "目标设定中",
  goal_pending_review: "待审核目标",
  goal_rejected: "目标被驳回",
  self_review: "自评中",
  manager_review: "上级评分中",
  dual_manager_review: "上级并行评分中",
  dotted_manager_review: "虚线上级评分中",
  final_review: "待校准",
  issued: "待员工确认",
  completed: "已完成",
};

export const PERFORMANCE_STATUS_TONES: Record<PerformanceStatus, PerformanceStatusTone> = {
  template_selection: "info",
  goal_setting: "info",
  goal_pending_review: "warning",
  goal_rejected: "danger",
  self_review: "success",
  manager_review: "primary",
  dual_manager_review: "primary",
  dotted_manager_review: "warning",
  final_review: "warning",
  issued: "warning",
  completed: "success",
};

/** 绩效列表等筛选用：不含已弃用状态 */
export const PERFORMANCE_FILTER_STATUSES: PerformanceStatus[] = (
  Object.keys(PERFORMANCE_STATUS_LABELS) as PerformanceStatus[]
).filter((s) => !DEPRECATED_PERFORMANCE_STATUSES.includes(s));

export function performanceStatusLabel(status: PerformanceStatus | string): string {
  const key = normalizePerformanceStatus(status);
  return PERFORMANCE_STATUS_LABELS[key] ?? String(status);
}

export function statusToneBadgeClass(tone: PerformanceStatusTone): string {
  if (tone === "primary") return "ui-badge-primary";
  return `ui-badge-${tone}`;
}

export function statusToneRowClass(tone: PerformanceStatusTone): string {
  if (tone === "success") {
    return "border-l-4 border-l-[var(--success)] border-border bg-[var(--success-bg)] hover:border-[var(--success)]/45";
  }
  if (tone === "warning") {
    return "border-l-4 border-l-[var(--warning)] border-border bg-[var(--warning-bg)] hover:border-[var(--warning)]/45";
  }
  if (tone === "danger") {
    return "border-l-4 border-l-[var(--danger)] border-border bg-[var(--danger-bg)] hover:border-[var(--danger)]/45";
  }
  if (tone === "primary") {
    return "border-l-4 border-l-primary border-border bg-[color-mix(in_srgb,var(--primary)_8%,var(--card))] hover:border-primary/45";
  }
  if (tone === "neutral") {
    return "border-l-4 border-l-border border-border bg-card hover:bg-accent";
  }
  return "border-l-4 border-l-[var(--info)] border-border bg-[var(--info-bg)] hover:border-[var(--info)]/45";
}

export function statusToneActionClass(tone: PerformanceStatusTone): string {
  return `ui-btn ui-btn-sm ui-btn-tone-${tone}`;
}
