<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import type {
  CreatePerformanceResponse,
  DepartmentOption,
  EmployeeDirectoryListItem,
  FeishuSubjectOption,
  ScoringScheme,
} from "@/types/api.interface";
import { createPerformance, getDefaultNodeDeadlines, getPerformanceMonthPeriods } from "@/api/performances";
import type { PerformanceNodeDeadlineKey } from "@/types/api.interface";
import { getAllEmployees, getDepartmentOptions, getFeishuLoginSubjects } from "@/api/employees";
import { listScoringSchemes } from "@/api/scoringSchemes";
import AssessmentMonthPicker from "@/components/performance/AssessmentMonthPicker.vue";

const open = defineModel<boolean>("open", { default: false });
const emit = defineEmits<{ success: [] }>();

type TargetType = "all" | "department" | "employee";
type Period = { periodKey: string; name: string };

const loading = ref(false);
const submitting = ref(false);
const employees = ref<EmployeeDirectoryListItem[]>([]);
const departmentOptions = ref<DepartmentOption[]>([]);
const periods = ref<Period[]>([]);
const feishuSubjects = ref<FeishuSubjectOption[]>([]);
const subjectCode = ref("");
const targetType = ref<TargetType>("employee");
const selectedEmployees = ref<string[]>([]);
const period = ref("");
const scoringSchemeId = ref("");
const schemeList = ref<ScoringScheme[]>([]);
const schemeSelectHint = ref("");
const query = ref("");
const result = ref<CreatePerformanceResponse | null>(null);
const showResult = ref(false);
const message = ref<string | null>(null);

const NODE_DEADLINE_LABELS: { key: PerformanceNodeDeadlineKey; label: string }[] = [
  { key: "goal", label: "目标设定及审核截止" },
  { key: "scoring", label: "员工及上级评分截止" },
  { key: "final", label: "绩效校准截止" },
  { key: "confirm", label: "员工确认截止" },
];

const nodeDeadlines = ref<Partial<Record<PerformanceNodeDeadlineKey, string>>>({});

watch(open, (isOpen) => {
  document.body.style.overflow = isOpen ? "hidden" : "";
});
onUnmounted(() => {
  document.body.style.overflow = "";
});

function matchesDept(emp: EmployeeDirectoryListItem, dept: DepartmentOption): boolean {
  const did = emp.departmentId?.trim();
  if (did) return did === dept.id;
  const dn = emp.departmentName?.trim();
  const dnm = dept.name?.trim();
  return !!(dn && dnm && dn === dnm);
}

const filteredDepartmentOptions = computed(() => {
  const q = query.value.trim().toLowerCase();
  const base = [...departmentOptions.value].sort((a, b) => {
    const an = (a.name ?? a.id ?? "").toString();
    const bn = (b.name ?? b.id ?? "").toString();
    return an.localeCompare(bn, "zh-CN");
  });
  if (!q) return base;
  return base.filter((dept) => {
    const label = (dept.name ?? dept.id ?? "").toLowerCase();
    if (label.includes(q)) return true;
    return employees.value.some((e) => matchesDept(e, dept) && e.name.toLowerCase().includes(q));
  });
});

function employeesInDept(dept: DepartmentOption): EmployeeDirectoryListItem[] {
  const q = query.value.trim().toLowerCase();
  return employees.value.filter((e) => {
    if (!matchesDept(e, dept)) return false;
    if (!q) return true;
    if ((dept.name ?? "").toLowerCase().includes(q)) return true;
    return e.name.toLowerCase().includes(q);
  });
}

function deptEmployeeIds(dept: DepartmentOption): string[] {
  const ids = employees.value.filter((e) => matchesDept(e, dept)).map((e) => e.userId);
  return Array.from(new Set(ids));
}

/** 部门行复选框：有任意成员被选中即显示勾选（不要求全员），与「少选一人仍打勾」的期望一致 */
function deptHeaderChecked(dept: DepartmentOption): boolean {
  const ids = deptEmployeeIds(dept);
  return ids.some((id) => selectedEmployees.value.includes(id));
}

function toggleDeptSelectAll(dept: DepartmentOption, checked: boolean) {
  const ids = deptEmployeeIds(dept);
  if (checked) {
    selectedEmployees.value = Array.from(new Set([...selectedEmployees.value, ...ids]));
    return;
  }
  selectedEmployees.value = selectedEmployees.value.filter((id) => !ids.includes(id));
}

const filteredEmployees = computed(() => {
  const q = query.value.trim().toLowerCase();
  if (!q) return employees.value;
  return employees.value.filter(
    (e) => e.name.toLowerCase().includes(q) || (e.departmentName ?? "").toLowerCase().includes(q),
  );
});

const totalSelectedCount = computed(() => {
  if (targetType.value === "all") return new Set(employees.value.map((e) => e.userId)).size;
  return selectedEmployees.value.length;
});

const failedResults = computed(() => result.value?.results.filter((row) => !row.success) ?? []);

function schemeWeightSummary(s: ScoringScheme): string {
  const parts: string[] = [];
  if (s.performanceWeight > 0) parts.push(`绩效 ${s.performanceWeight}%`);
  if (s.cultureWeight > 0) parts.push(`文化 ${s.cultureWeight}%`);
  if (s.learningWeight > 0) parts.push(`学习 ${s.learningWeight}%`);
  return parts.length ? `${s.name}（${parts.join(" / ")}）` : s.name;
}

const allEmployeesSelected = computed(
  () =>
    filteredEmployees.value.length > 0 &&
    filteredEmployees.value.every((e) => selectedEmployees.value.includes(e.userId)),
);

watch(open, async (value) => {
  if (!value) return;
  resetForm();
  await loadFeishuSubjects();
  subjectCode.value = feishuSubjects.value[0]?.code ?? "";
  if (!subjectCode.value.trim()) {
    void loadData();
  }
});

watch(subjectCode, (code) => {
  if (!open.value || showResult.value) return;
  selectedEmployees.value = [];
  const c = (code ?? "").trim();
  if (!c) {
    employees.value = [];
    departmentOptions.value = [];
    return;
  }
  void loadData();
});

watch(targetType, () => {
  query.value = "";
  selectedEmployees.value = [];
});

watch(period, async (p) => {
  if (!p?.trim()) {
    nodeDeadlines.value = {};
    return;
  }
  try {
    const res = await getDefaultNodeDeadlines(p);
    const d = res.nodeDeadlines ?? {};
    nodeDeadlines.value = {
      goal: d.goal,
      scoring: d.scoring,
      final: d.final,
      confirm: d.confirm,
    };
  } catch {
    nodeDeadlines.value = {};
  }
});

function resetForm() {
  targetType.value = "employee";
  selectedEmployees.value = [];
  period.value = "";
  scoringSchemeId.value = "";
  schemeSelectHint.value = "";
  query.value = "";
  subjectCode.value = "";
  result.value = null;
  showResult.value = false;
  message.value = null;
  nodeDeadlines.value = {};
}

async function loadFeishuSubjects() {
  try {
    const res = await getFeishuLoginSubjects();
    feishuSubjects.value = res.items || [];
  } catch {
    feishuSubjects.value = [];
  }
}

async function loadData() {
  loading.value = true;
  try {
    const code = subjectCode.value.trim();
    const [ps, schemesRes] = await Promise.all([getPerformanceMonthPeriods(), listScoringSchemes(1, 500)]);
    periods.value = ps.items ?? [];
    const allSchemes = schemesRes.list ?? [];
    schemeList.value = allSchemes.filter((s) => s.status === "enabled");
    schemeSelectHint.value =
      allSchemes.length > 0 && schemeList.value.length === 0
        ? `已拉取 ${allSchemes.length} 个评分方案，均为「停用」。请先到「评分方案」页启用后再选。`
        : "";
    if (!code) {
      employees.value = [];
      departmentOptions.value = [];
      message.value = feishuSubjects.value.length ? "请先选择飞书主体" : "未配置飞书登录主体，请联系管理员";
      return;
    }
    const [emps, deptRes] = await Promise.all([
      getAllEmployees({ subjectCode: code }),
      getDepartmentOptions({ subjectCode: code }),
    ]);
    employees.value = emps.items ?? [];
    departmentOptions.value = deptRes.items ?? [];
    message.value = null;
  } catch {
    employees.value = [];
    departmentOptions.value = [];
    periods.value = [];
    schemeList.value = [];
    schemeSelectHint.value = "";
    message.value = "加载数据失败，请重试";
  } finally {
    loading.value = false;
  }
}

function close() {
  open.value = false;
}

function toggleEmployee(userId: string, checked: boolean) {
  if (checked) {
    if (!selectedEmployees.value.includes(userId)) {
      selectedEmployees.value = [...selectedEmployees.value, userId];
    }
    return;
  }
  selectedEmployees.value = selectedEmployees.value.filter((item) => item !== userId);
}

function toggleSelectAllEmployees() {
  const ids = filteredEmployees.value.map((e) => e.userId);
  if (allEmployeesSelected.value) {
    selectedEmployees.value = selectedEmployees.value.filter((id) => !ids.includes(id));
    return;
  }
  selectedEmployees.value = Array.from(new Set([...selectedEmployees.value, ...ids]));
}

function getSelectedEmployeeIds(): string[] {
  if (targetType.value === "all") return Array.from(new Set(employees.value.map((e) => e.userId)));
  return selectedEmployees.value;
}

async function submit() {
  const employeeIds = getSelectedEmployeeIds();
  if (!subjectCode.value.trim()) {
    message.value = "请先选择飞书主体";
    return;
  }
  if (!employeeIds.length) {
    message.value = "请至少选择一名员工";
    return;
  }
  if (!period.value) {
    message.value = "请选择考核月度";
    return;
  }
  if (!scoringSchemeId.value) {
    message.value = "请选择评分方案";
    return;
  }
  submitting.value = true;
  message.value = null;
  try {
    result.value = await createPerformance({
      employeeIds,
      period: period.value,
      scoringSchemeId: scoringSchemeId.value,
      subjectCode: subjectCode.value.trim(),
      nodeDeadlines: { ...nodeDeadlines.value },
    });
    showResult.value = true;
  } catch (e) {
    message.value = e instanceof Error ? e.message : "创建绩效失败";
  } finally {
    submitting.value = false;
  }
}

function finish() {
  close();
  emit("success");
}
</script>

<template>
  <div v-if="open" class="ui-dialog-backdrop" @click.self="close">
    <div class="ui-dialog-panel max-w-xl">
      <h2 class="text-lg font-semibold">创建绩效评估</h2>

      <template v-if="showResult && result">
        <div class="mt-4 space-y-2 text-sm">
          <p>总计：{{ result.total }} 条</p>
          <p class="text-success">成功：{{ result.successCount }} 条</p>
          <p v-if="result.failCount > 0" class="text-destructive">失败：{{ result.failCount }} 条</p>
        </div>
        <div v-if="failedResults.length > 0" class="mt-4 max-h-40 space-y-1 overflow-y-auto rounded-md border border-border p-3">
          <p v-for="(row, idx) in failedResults" :key="idx" class="text-sm text-destructive">
            {{ row.employeeName || row.employeeId }}：{{ row.error || "失败" }}
          </p>
        </div>
        <div class="mt-6 flex justify-end">
          <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="finish">
            完成
          </button>
        </div>
      </template>

      <template v-else>
        <p v-if="loading" class="mt-6 py-8 text-center text-sm text-muted-foreground">加载中...</p>
        <template v-else>
          <label class="mt-4 block text-sm font-medium">飞书主体</label>
          <select v-model="subjectCode" class="mt-1 w-full rounded-md border border-border bg-card px-3 py-2 text-sm">
            <option v-if="!feishuSubjects.length" value="">未配置主体</option>
            <option v-for="s in feishuSubjects" :key="s.code" :value="s.code">{{ s.name }}（{{ s.code }}）</option>
          </select>
          <p v-if="!feishuSubjects.length" class="mt-1 text-xs text-muted-foreground">请先在系统中配置飞书登录应用与主体。</p>

          <label class="mt-4 block text-sm font-medium">评估对象类型</label>
          <select v-model="targetType" class="mt-1 w-full rounded-md border border-border bg-card px-3 py-2 text-sm">
            <option value="all">全员</option>
            <option value="department">部门</option>
            <option value="employee">员工</option>
          </select>

          <div v-if="targetType === 'all'" class="mt-4 rounded-md border border-border bg-accent/30 p-3 text-sm">
            已选择 <span class="font-semibold text-primary">{{ employees.length }}</span> 人（全员）
          </div>

          <template v-else-if="targetType === 'department'">
            <div class="mt-4 flex items-center justify-between">
              <label class="text-sm font-medium">
                部门与成员
                <span class="ml-2 text-xs text-muted-foreground">已选 {{ selectedEmployees.length }} 人</span>
              </label>
            </div>
            <input
              v-model="query"
              type="search"
              class="mt-2 w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
              placeholder="搜索部门或成员姓名"
            />
            <div class="mt-2 max-h-36 space-y-2 overflow-y-auto rounded-md border border-border p-2 md:max-h-72">
              <details
                v-for="dept in filteredDepartmentOptions"
                :key="dept.id"
                class="rounded-md border border-border bg-card open:bg-accent/20"
              >
                <summary class="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-sm [&::-webkit-details-marker]:hidden">
                  <input
                    type="checkbox"
                    class="shrink-0"
                    :checked="deptHeaderChecked(dept)"
                    @click.stop
                    @change="toggleDeptSelectAll(dept, ($event.target as HTMLInputElement).checked)"
                  />
                  <span class="font-medium">{{ dept.name || dept.id }}</span>
                  <span class="text-xs text-muted-foreground">（{{ deptEmployeeIds(dept).length }} 人）</span>
                </summary>
                <div class="space-y-2 border-t border-border px-3 py-2 pl-10">
                  <label
                    v-for="emp in employeesInDept(dept)"
                    :key="emp.userId"
                    class="flex cursor-pointer items-center gap-2 text-sm"
                  >
                    <input
                      type="checkbox"
                      :checked="selectedEmployees.includes(emp.userId)"
                      @change="toggleEmployee(emp.userId, ($event.target as HTMLInputElement).checked)"
                    />
                    <span>{{ emp.name }}</span>
                    <span v-if="!emp.assessmentRuleId" class="text-xs text-[var(--warning)]">未绑规则</span>
                  </label>
                  <p v-if="employeesInDept(dept).length === 0" class="text-xs text-muted-foreground">无匹配成员</p>
                </div>
              </details>
              <p v-if="filteredDepartmentOptions.length === 0" class="py-6 text-center text-sm text-muted-foreground">
                暂无部门数据，请先同步员工或检查主体
              </p>
            </div>
          </template>

          <template v-else>
            <div class="mt-4 flex items-center justify-between">
              <label class="text-sm font-medium">
                选择员工
                <span class="ml-2 text-xs text-muted-foreground">已选 {{ selectedEmployees.length }} 人</span>
              </label>
              <button type="button" class="text-xs text-primary hover:underline" @click="toggleSelectAllEmployees">
                {{ allEmployeesSelected ? "取消全选" : "全选" }}
              </button>
            </div>
            <input
              v-model="query"
              type="search"
              class="mt-2 w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
              placeholder="搜索姓名或部门"
            />
            <div class="mt-2 max-h-36 space-y-2 overflow-y-auto rounded-md border border-border p-3 md:max-h-52">
              <label
                v-for="emp in filteredEmployees"
                :key="emp.userId"
                class="flex cursor-pointer items-center gap-2 text-sm"
              >
                <input
                  type="checkbox"
                  :checked="selectedEmployees.includes(emp.userId)"
                  @change="toggleEmployee(emp.userId, ($event.target as HTMLInputElement).checked)"
                />
                <span>{{ emp.name }}</span>
                <span v-if="emp.departmentName" class="text-muted-foreground">（{{ emp.departmentName }}）</span>
                <span v-if="!emp.assessmentRuleId" class="text-xs text-[var(--warning)]">未绑规则</span>
              </label>
              <p v-if="filteredEmployees.length === 0" class="text-center text-sm text-muted-foreground">无匹配员工</p>
            </div>
          </template>

          <label class="mt-4 block text-sm font-medium">考核月度</label>
          <AssessmentMonthPicker v-model="period" class="mt-1" :items="periods" :disabled="loading" />

          <div v-if="period" class="mt-4 space-y-3 rounded-md border border-border p-3">
            <p class="text-sm font-medium">节点截止时间</p>
            <p class="text-xs text-muted-foreground">创建时写入每条绩效，详情页仅展示；可按需调整下列默认值。</p>
            <div v-for="item in NODE_DEADLINE_LABELS" :key="item.key" class="grid grid-cols-1 gap-1 sm:grid-cols-[9rem_1fr] sm:items-center">
              <label class="text-xs text-muted-foreground">{{ item.label }}</label>
              <input
                v-model="nodeDeadlines[item.key]"
                type="date"
                class="w-full rounded-md border border-border bg-card px-3 py-2 text-sm"
              />
            </div>
          </div>

          <label class="mt-4 block text-sm font-medium">评分方案</label>
          <select v-model="scoringSchemeId" class="mt-1 w-full rounded-md border border-border bg-card px-3 py-2 text-sm">
            <option value="">请选择评分方案</option>
            <option v-for="s in schemeList" :key="s.id" :value="s.id">
              {{ schemeWeightSummary(s) }}
            </option>
          </select>
          <p class="mt-1 text-xs text-muted-foreground">定义绩效、文化价值观、学习与成长的评分占比</p>
          <p v-if="schemeSelectHint" class="mt-1 text-xs text-[var(--warning)]">{{ schemeSelectHint }}</p>

          <p class="mt-3 text-xs text-muted-foreground">
            考核规则请在「员工管理」中为每位员工绑定；未绑定或规则已停用的员工将无法创建，结果会在下方失败明细中列出。
          </p>

          <p class="mt-2 text-xs text-muted-foreground">将创建 {{ totalSelectedCount }} 名员工的绩效记录</p>

          <p v-if="message" class="mt-3 text-sm text-destructive">{{ message }}</p>

          <div class="mt-6 flex justify-end gap-3">
            <button type="button" class="rounded-md border border-border px-4 py-2 text-sm" @click="close">取消</button>
            <button
              type="button"
              class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
              :disabled="submitting"
              @click="submit"
            >
              {{ submitting ? "提交中..." : "创建" }}
            </button>
          </div>
        </template>
      </template>
    </div>
  </div>
</template>
