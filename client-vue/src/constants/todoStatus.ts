import type { PerformanceStatus, TodoItem, TodoType } from "@/types/api.interface";
import {
  PERFORMANCE_STATUS_TONES,
  performanceStatusLabel,
  type PerformanceStatusTone,
  statusToneActionClass,
  statusToneBadgeClass,
  statusToneRowClass,
} from "@/constants/performanceStatus";

export type TodoCategory = "acceptance" | "score" | "self" | "goal" | "flow";

export interface TodoMeta {
  label: string;
  category: TodoCategory;
  categoryLabel: string;
  tone: PerformanceStatusTone;
  actionLabel: string;
  urgent: boolean;
}

type TodoMetaCore = Omit<TodoMeta, "label">;

const TODO_META_CORE: Record<TodoType, TodoMetaCore> = {
  template_selection: {
    category: "goal",
    categoryLabel: "目标",
    tone: PERFORMANCE_STATUS_TONES.template_selection,
    actionLabel: "去设定",
    urgent: true,
  },
  goal_setting: {
    category: "goal",
    categoryLabel: "目标",
    tone: PERFORMANCE_STATUS_TONES.goal_setting,
    actionLabel: "去设定",
    urgent: true,
  },
  goal_rejected: {
    category: "goal",
    categoryLabel: "目标",
    tone: PERFORMANCE_STATUS_TONES.goal_rejected,
    actionLabel: "去修改",
    urgent: true,
  },
  goal_pending_review: {
    category: "acceptance",
    categoryLabel: "验收",
    tone: PERFORMANCE_STATUS_TONES.goal_pending_review,
    actionLabel: "去验收",
    urgent: true,
  },
  self_review: {
    category: "self",
    categoryLabel: "自评",
    tone: PERFORMANCE_STATUS_TONES.self_review,
    actionLabel: "去自评",
    urgent: true,
  },
  manager_review: {
    category: "score",
    categoryLabel: "评分",
    tone: PERFORMANCE_STATUS_TONES.manager_review,
    actionLabel: "去评分",
    urgent: true,
  },
  dual_manager_review: {
    category: "score",
    categoryLabel: "评分",
    tone: PERFORMANCE_STATUS_TONES.dual_manager_review,
    actionLabel: "去评分",
    urgent: true,
  },
  dotted_manager_review: {
    category: "score",
    categoryLabel: "评分",
    tone: PERFORMANCE_STATUS_TONES.dotted_manager_review,
    actionLabel: "去评分",
    urgent: true,
  },
  plan_execution: {
    category: "flow",
    categoryLabel: "流程",
    tone: PERFORMANCE_STATUS_TONES.plan_execution,
    actionLabel: "去处理",
    urgent: false,
  },
  final_review: {
    category: "flow",
    categoryLabel: "流程",
    tone: PERFORMANCE_STATUS_TONES.final_review,
    actionLabel: "去校准",
    urgent: false,
  },
  issued: {
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
  const t = normalizeTodoType(type);
  return {
    ...TODO_META_CORE[type],
    label: performanceStatusLabel(t as PerformanceStatus),
  };
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

export type TodoBucket = "mine" | "team" | "flow";

/** 创建人负责的流程节点（计划执行、待校准），单独成组且不计入角标。 */
const TODO_CREATOR_FLOW_TYPES: ReadonlySet<TodoType> = new Set(["plan_execution", "final_review"]);

/** 仅当「绩效主体员工本人」需要操作时，才归入「我的待办」。 */
const TODO_EMPLOYEE_ACTS_ONLY: ReadonlySet<TodoType> = new Set([
  "goal_setting",
  "goal_rejected",
  "self_review",
  "issued",
]);

/** 是否计入待办页/飞书应用角标的「待处理」数量。 */
export function countsTowardTodoBadge(type: TodoType): boolean {
  return !TODO_CREATOR_FLOW_TYPES.has(type);
}

export function todoBucket(type: TodoType, item?: TodoItem, userId?: string | null): TodoBucket {
  if (TODO_CREATOR_FLOW_TYPES.has(type)) {
    return "flow";
  }
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
): { mine: TodoItem[]; team: TodoItem[]; flow: TodoItem[] } {
  const mine: TodoItem[] = [];
  const team: TodoItem[] = [];
  const flow: TodoItem[] = [];
  for (const item of items) {
    const bucket = todoBucket(item.type, item, userId);
    if (bucket === "mine") mine.push(item);
    else if (bucket === "flow") flow.push(item);
    else team.push(item);
  }
  return { mine, team, flow };
}

export function countTodosForBadge(items: TodoItem[]): number {
  return items.filter((item) => countsTowardTodoBadge(item.type)).length;
}
