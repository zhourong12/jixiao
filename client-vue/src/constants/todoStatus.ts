import type { PerformanceStatus, TodoItem, TodoType } from "@/types/api.interface";
import {
  PERFORMANCE_STATUS_TONES,
  type PerformanceStatusTone,
  statusToneActionClass,
  statusToneBadgeClass,
  statusToneRowClass,
} from "@/constants/performanceStatus";

export type TodoCategory = "acceptance" | "score" | "self" | "goal";

export interface TodoMeta {
  label: string;
  category: TodoCategory;
  categoryLabel: string;
  tone: PerformanceStatusTone;
  actionLabel: string;
  urgent: boolean;
}

export const TODO_STATUS_LABEL: Record<TodoType, string> = {
  template_selection: "选择模板",
  goal_setting: "目标设定",
  goal_rejected: "目标驳回",
  goal_pending_review: "目标验收",
  self_review: "自评",
  manager_review: "主管评分",
  dual_manager_review: "主管并行评分",
  dotted_manager_review: "虚线主管评分",
  final_review: "终审验收",
  issued: "结果确认",
};

const TODO_META: Record<TodoType, TodoMeta> = {
  template_selection: {
    label: TODO_STATUS_LABEL.template_selection,
    category: "goal",
    categoryLabel: "目标",
    tone: PERFORMANCE_STATUS_TONES.template_selection,
    actionLabel: "去选择",
    urgent: true,
  },
  goal_setting: {
    label: TODO_STATUS_LABEL.goal_setting,
    category: "goal",
    categoryLabel: "目标",
    tone: PERFORMANCE_STATUS_TONES.goal_setting,
    actionLabel: "去设定",
    urgent: true,
  },
  goal_rejected: {
    label: TODO_STATUS_LABEL.goal_rejected,
    category: "goal",
    categoryLabel: "目标",
    tone: PERFORMANCE_STATUS_TONES.goal_rejected,
    actionLabel: "去修改",
    urgent: true,
  },
  goal_pending_review: {
    label: TODO_STATUS_LABEL.goal_pending_review,
    category: "acceptance",
    categoryLabel: "验收",
    tone: PERFORMANCE_STATUS_TONES.goal_pending_review,
    actionLabel: "去验收",
    urgent: true,
  },
  self_review: {
    label: TODO_STATUS_LABEL.self_review,
    category: "self",
    categoryLabel: "自评",
    tone: PERFORMANCE_STATUS_TONES.self_review,
    actionLabel: "去自评",
    urgent: true,
  },
  manager_review: {
    label: TODO_STATUS_LABEL.manager_review,
    category: "score",
    categoryLabel: "评分",
    tone: PERFORMANCE_STATUS_TONES.manager_review,
    actionLabel: "去评分",
    urgent: true,
  },
  dual_manager_review: {
    label: TODO_STATUS_LABEL.dual_manager_review,
    category: "score",
    categoryLabel: "评分",
    tone: PERFORMANCE_STATUS_TONES.dual_manager_review,
    actionLabel: "去评分",
    urgent: true,
  },
  dotted_manager_review: {
    label: TODO_STATUS_LABEL.dotted_manager_review,
    category: "score",
    categoryLabel: "评分",
    tone: PERFORMANCE_STATUS_TONES.dotted_manager_review,
    actionLabel: "去评分",
    urgent: true,
  },
  final_review: {
    label: TODO_STATUS_LABEL.final_review,
    category: "acceptance",
    categoryLabel: "验收",
    tone: PERFORMANCE_STATUS_TONES.final_review,
    actionLabel: "去验收",
    urgent: true,
  },
  issued: {
    label: TODO_STATUS_LABEL.issued,
    category: "acceptance",
    categoryLabel: "验收",
    tone: PERFORMANCE_STATUS_TONES.issued,
    actionLabel: "去确认",
    urgent: true,
  },
};

export function normalizeTodoType(type: TodoType): TodoType {
  return type === "template_selection" ? "goal_setting" : type;
}

export function getTodoMeta(type: TodoType): TodoMeta {
  return TODO_META[normalizeTodoType(type)];
}

export function todoTone(type: TodoType): PerformanceStatusTone {
  return PERFORMANCE_STATUS_TONES[normalizeTodoType(type) as PerformanceStatus];
}

export function todoBadgeClass(type: TodoType): string {
  return statusToneBadgeClass(todoTone(type));
}

export function todoRowClass(type: TodoType): string {
  const meta = getTodoMeta(type);
  const urgent = meta.urgent ? " shadow-sm" : "";
  return `ui-todo-item ${statusToneRowClass(meta.tone)}${urgent}`;
}

export function todoActionClass(type: TodoType): string {
  return statusToneActionClass(todoTone(type));
}

export type TodoBucket = "mine" | "team";

/** 仅当「绩效主体员工本人」需要操作时，才归入「我的待办」；上级评分/目标验收/终审等不算员工本人待办。 */
const TODO_EMPLOYEE_ACTS_ONLY: ReadonlySet<TodoType> = new Set([
  "goal_setting",
  "goal_rejected",
  "self_review",
  "issued",
]);

export function todoBucket(type: TodoType, item?: TodoItem, userId?: string | null): TodoBucket {
  if (!userId) {
    const category = getTodoMeta(type).category;
    return category === "self" || category === "goal" ? "mine" : "team";
  }
  if (item?.employeeId) {
    if (item.employeeId !== userId) {
      return "team";
    }
    if (TODO_EMPLOYEE_ACTS_ONLY.has(type)) {
      return "mine";
    }
    return "team";
  }
  const category = getTodoMeta(type).category;
  return category === "self" || category === "goal" ? "mine" : "team";
}

export function groupTodosByBucket(
  items: TodoItem[],
  userId?: string | null,
): { mine: TodoItem[]; team: TodoItem[] } {
  const mine: TodoItem[] = [];
  const team: TodoItem[] = [];
  for (const item of items) {
    if (todoBucket(item.type, item, userId) === "mine") mine.push(item);
    else team.push(item);
  }
  return { mine, team };
}
