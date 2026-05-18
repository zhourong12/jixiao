export const EVALUATION_PERIOD_STATUS_LABELS: Record<string, string> = {
  draft: "草稿",
  open: "开放",
  closed: "已关闭",
};

export function evaluationPeriodStatusLabel(status?: string): string {
  const key = status?.trim();
  if (!key) return "—";
  return EVALUATION_PERIOD_STATUS_LABELS[key] ?? key;
}
