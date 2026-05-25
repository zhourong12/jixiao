import type { PerformanceStatus } from "@/types/api.interface";
import { normalizePerformanceStatus } from "@/constants/performanceStatus";

export type PerformanceDetailStep = {
  key: string;
  label: string;
  statuses: PerformanceStatus[];
};

export const PERFORMANCE_DETAIL_STEPS: PerformanceDetailStep[] = [
  { key: "goal", label: "目标设定", statuses: ["goal_setting", "goal_rejected", "template_selection"] },
  { key: "goal_review", label: "目标审核", statuses: ["goal_pending_review"] },
  { key: "plan", label: "计划执行中", statuses: ["plan_execution"] },
  { key: "self", label: "员工自评", statuses: ["self_review"] },
  {
    key: "manager",
    label: "上级评分",
    statuses: ["manager_review", "dotted_manager_review", "dual_manager_review"],
  },
  { key: "final", label: "绩效校准", statuses: ["final_review"] },
  { key: "confirm", label: "员工确认", statuses: ["issued"] },
  { key: "done", label: "已完成", statuses: ["completed"] },
];

export const PERFORMANCE_DETAIL_STATUS: Record<
  PerformanceStatus,
  { label: string; description: string; badgeClass: string }
> = {
  template_selection: {
    label: "目标设定中",
    description: "请填写绩效目标",
    badgeClass: "bg-blue-100 text-blue-800",
  },
  goal_setting: {
    label: "目标设定中",
    description: "请填写绩效目标",
    badgeClass: "bg-blue-100 text-blue-800",
  },
  goal_pending_review: {
    label: "待审核目标",
    description: "等待上级审核目标",
    badgeClass: "bg-yellow-100 text-yellow-800",
  },
  goal_rejected: {
    label: "目标被驳回",
    description: "目标需修改后重新提交",
    badgeClass: "bg-red-100 text-red-800",
  },
  plan_execution: {
    label: "计划执行中",
    description: "目标已审核通过，请在截止日前完成计划执行；到期后可进入自评",
    badgeClass: "bg-sky-100 text-sky-800",
  },
  self_review: {
    label: "自评中",
    description: "请完成自评",
    badgeClass: "bg-blue-100 text-blue-800",
  },
  manager_review: {
    label: "上级评分中",
    description: "等待直属上级评分",
    badgeClass: "bg-purple-100 text-purple-800",
  },
  dual_manager_review: {
    label: "上级并行评分中",
    description: "直属与虚线上级并行评分，双方均提交后进入校准",
    badgeClass: "bg-purple-100 text-purple-800",
  },
  dotted_manager_review: {
    label: "虚线上级评分中",
    description: "等待虚线上级评分",
    badgeClass: "bg-purple-100 text-purple-800",
  },
  final_review: {
    label: "待校准",
    description: "等待创建该绩效的管理员校准并下发结果",
    badgeClass: "bg-orange-100 text-orange-800",
  },
  issued: {
    label: "待员工确认",
    description: "绩效结果已下发，等待员工确认签收",
    badgeClass: "bg-yellow-100 text-yellow-800",
  },
  completed: {
    label: "已完成",
    description: "绩效评估已完成",
    badgeClass: "bg-green-100 text-green-800",
  },
};

export function performanceDetailStepIndex(status: PerformanceStatus): number {
  const normalized = normalizePerformanceStatus(status);
  return PERFORMANCE_DETAIL_STEPS.findIndex((step) => step.statuses.includes(normalized));
}
