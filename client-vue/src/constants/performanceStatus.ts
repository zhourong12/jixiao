import type { PerformanceStatus } from "@/types/api.interface";

export const PERFORMANCE_STATUS_LABELS: Record<PerformanceStatus, string> = {
  template_selection: "待选择模板",
  goal_setting: "目标设定中",
  goal_pending_review: "待审核目标",
  goal_rejected: "目标被驳回",
  self_review: "自评中",
  manager_review: "上级评分中",
  dual_manager_review: "上级并行评分中",
  dotted_manager_review: "虚线上级评分中",
  final_review: "待校准",
  completed: "已完成",
};
