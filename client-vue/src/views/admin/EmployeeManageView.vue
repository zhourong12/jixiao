<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type {
  AssessmentRuleListItem,
  CreateEmployeeRequest,
  DepartmentTreeSubject,
  EmployeeDirectoryListItem,
  EmployeeListItem,
  FeishuSubjectOption,
  FeishuUserOptionItem,
  UpdateEmployeeRequest,
} from "@/types/api.interface";
import {
  createEmployee,
  deleteEmployee,
  getDepartmentTree,
  getEmployeeRoleOptions,
  getEmployees,
  subjectCodeQueryValue,
  getFeishuLoginSubjects,
  getFeishuUserProfile,
  getFeishuUserOptions,
  getCalibrationAssignees,
  getAllEmployees,
  setCalibrationAssignees,
  updateEmployee,
  updateEmployeeHierarchy,
} from "@/api/employees";
import { listAssessmentRules } from "@/api/assessmentRules";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import EmployeeSupervisorSelect from "@/components/admin/EmployeeSupervisorSelect.vue";
import ExcelImportDialog from "@/components/admin/ExcelImportDialog.vue";
import DepartmentTreeSelect from "@/components/business-ui/DepartmentTreeSelect.vue";
import ListPagination from "@/components/ui/ListPagination.vue";
import {
  departmentFieldsFromFilter,
  employeeRowToDepartmentFilter,
  matchDepartmentFilterByName,
  parseDepartmentFilter,
} from "@/utils/departmentFilter";

const pageSize = ref(10);
const employees = ref<EmployeeListItem[]>([]);
const total = ref(0);
const page = ref(1);
const keyword = ref("");
const loading = ref(false);
const message = ref<string | null>(null);
const formDialogError = ref<string | null>(null);

const formOpen = ref(false);
const editing = ref<EmployeeListItem | null>(null);
const form = ref<CreateEmployeeRequest & { feishuSubjectName?: string }>({ userId: "", name: "" });
const roleOptions = ref<{ roleKey: string; name: string }[]>([]);
const assessmentRuleOptions = ref<AssessmentRuleListItem[]>([]);
const feishuSubjects = ref<FeishuSubjectOption[]>([]);
/** 列表筛选：空字符串表示全部主体 */
const listFilterSubjectCode = ref("");
const departmentFilter = ref("");
const formDepartmentFilter = ref("");
const departmentTreeCache = ref<DepartmentTreeSubject[]>([]);
const feishuPickKeyword = ref("");
const feishuPickAllUsers = ref<FeishuUserOptionItem[]>([]);
const feishuPickDirectoryTotal = ref(0);
const feishuPickDirectoryTruncated = ref(false);
const feishuPickerOpen = ref(false);
const feishuPickLoading = ref(false);

type CachedFeishuDirectory = { items: FeishuUserOptionItem[]; total: number; truncated: boolean };
const feishuDirectoryCache = ref<Record<string, CachedFeishuDirectory>>({});
const feishuDirectoryInflight = new Map<string, Promise<void>>();

function applyDirectoryFromCache(subjectCode: string): boolean {
  const code = subjectCode.trim();
  const hit = feishuDirectoryCache.value[code];
  if (!hit) {
    feishuPickAllUsers.value = [];
    feishuPickDirectoryTotal.value = 0;
    feishuPickDirectoryTruncated.value = false;
    return false;
  }
  feishuPickAllUsers.value = hit.items;
  feishuPickDirectoryTotal.value = hit.total;
  feishuPickDirectoryTruncated.value = hit.truncated;
  return true;
}

async function fetchDirectoryIntoCache(subjectCode: string): Promise<void> {
  const code = subjectCode.trim();
  if (!code) return;
  if (feishuDirectoryCache.value[code]) return;
  let inflight = feishuDirectoryInflight.get(code);
  if (inflight) {
    await inflight;
    return;
  }
  inflight = (async () => {
    const res = await getFeishuUserOptions({ subjectCode: code });
    feishuDirectoryCache.value = {
      ...feishuDirectoryCache.value,
      [code]: {
        items: res.items || [],
        total: res.total ?? (res.items?.length ?? 0),
        truncated: Boolean(res.truncated),
      },
    };
  })();
  feishuDirectoryInflight.set(code, inflight);
  try {
    await inflight;
  } finally {
    feishuDirectoryInflight.delete(code);
  }
}

function prefetchAllFeishuDirectories() {
  for (const s of feishuSubjects.value) {
    void fetchDirectoryIntoCache(s.code).catch(() => {});
  }
}

const FEISHU_PICK_DISPLAY_CAP = 500;

const feishuPickFiltered = computed(() => {
  const kw = feishuPickKeyword.value.trim().toLowerCase();
  const list = feishuPickAllUsers.value;
  if (!kw) return list;
  return list.filter(
    (o) =>
      (o.name || "").toLowerCase().includes(kw) ||
      (o.feishuOpenId || "").toLowerCase().includes(kw),
  );
});

const feishuPickFilteredDisplay = computed(() => {
  const list = feishuPickFiltered.value;
  if (list.length <= FEISHU_PICK_DISPLAY_CAP) return list;
  return list.slice(0, FEISHU_PICK_DISPLAY_CAP);
});

const feishuPickDisplayHasMore = computed(() => feishuPickFiltered.value.length > FEISHU_PICK_DISPLAY_CAP);

const hierarchyOpen = ref(false);
const importOpen = ref(false);
const hierarchyTarget = ref<EmployeeListItem | null>(null);
const managerId = ref("");
const dottedManagerId = ref("");

const calibrationOpen = ref(false);
const calibrationLoading = ref(false);
const calibrationSaving = ref(false);
const calibrationAssigneeIds = ref<string[]>([]);
const calibrationDraftIds = ref<string[]>([]);
const calibrationDirectory = ref<EmployeeDirectoryListItem[]>([]);
const calibrationDraftIdsSet = computed(() => new Set(calibrationDraftIds.value));

const calibrationSummary = computed(() => {
  if (!calibrationAssigneeIds.value.length) {
    return "未配置（流程节点显示「管理员」）";
  }
  const names = calibrationAssigneeIds.value.map(
    (id) => calibrationDirectory.value.find((e) => e.userId === id)?.name || id,
  );
  if (names.length <= 4) {
    return `已选 ${names.length} 人：${names.join("、")}`;
  }
  return `已选 ${names.length} 人：${names.slice(0, 3).join("、")} 等`;
});

async function loadCalibrationPanel() {
  calibrationLoading.value = true;
  try {
    const [cfg, all] = await Promise.all([getCalibrationAssignees(), getAllEmployees()]);
    calibrationAssigneeIds.value = (cfg.items ?? []).map((x) => x.employeeId);
    const items = all.items ?? [];
    calibrationDirectory.value = items.slice().sort((a, b) => (a.name || "").localeCompare(b.name || "", "zh-CN"));
  } catch (e) {
    calibrationAssigneeIds.value = [];
    calibrationDirectory.value = [];
    message.value = e instanceof Error ? e.message : "加载绩效校准配置失败";
  } finally {
    calibrationLoading.value = false;
  }
}

function onCalibrationDraftToggle(employeeId: string, checked: boolean) {
  if (checked) {
    if (!calibrationDraftIds.value.includes(employeeId)) {
      calibrationDraftIds.value = [...calibrationDraftIds.value, employeeId];
    }
  } else {
    calibrationDraftIds.value = calibrationDraftIds.value.filter((id) => id !== employeeId);
  }
}

function openCalibrationDialog() {
  calibrationDraftIds.value = [...calibrationAssigneeIds.value];
  calibrationOpen.value = true;
  if (!calibrationDirectory.value.length && !calibrationLoading.value) {
    void loadCalibrationPanel();
  }
}

function closeCalibrationDialog() {
  calibrationOpen.value = false;
}

async function saveCalibrationAssignees() {
  calibrationSaving.value = true;
  try {
    await setCalibrationAssignees({ employeeIds: calibrationDraftIds.value });
    calibrationAssigneeIds.value = [...calibrationDraftIds.value];
    message.value = "已保存绩效校准负责人";
    calibrationOpen.value = false;
    await loadCalibrationPanel();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "保存失败";
  } finally {
    calibrationSaving.value = false;
  }
}

function listQueryFilter() {
  const dept = parseDepartmentFilter(departmentFilter.value);
  const subjectFromDropdown = subjectCodeQueryValue(listFilterSubjectCode.value);
  return {
    subjectCode: dept.subjectCode || subjectFromDropdown || undefined,
    departmentId: dept.departmentId,
  };
}

async function load() {
  loading.value = true;
  try {
    const { subjectCode, departmentId } = listQueryFilter();
    const result = await getEmployees({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      subjectCode,
      departmentId,
    });
    employees.value = result.items;
    total.value = result.total;
  } catch (e) {
    employees.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadFeishuSubjects() {
  try {
    const res = await getFeishuLoginSubjects();
    feishuSubjects.value = res.items || [];
  } catch {
    feishuSubjects.value = [];
  }
}

async function loadAssessmentRules() {
  try {
    const res = await listAssessmentRules(1, 500);
    assessmentRuleOptions.value = (res.items ?? []).filter((r) => r.status === "enabled");
  } catch {
    assessmentRuleOptions.value = [];
  }
}

async function loadRoles() {
  try {
    const res = await getEmployeeRoleOptions();
    roleOptions.value = res.items;
  } catch {
    roleOptions.value = [];
  }
}

async function ensureDepartmentTree() {
  if (departmentTreeCache.value.length) return;
  try {
    const res = await getDepartmentTree();
    departmentTreeCache.value = res.items ?? [];
  } catch {
    departmentTreeCache.value = [];
  }
}

function applyFormDepartmentToBody(body: CreateEmployeeRequest | UpdateEmployeeRequest) {
  const fields = departmentFieldsFromFilter(formDepartmentFilter.value, departmentTreeCache.value);
  if (!fields.departmentId || !fields.departmentName) {
    throw new Error("请选择部门");
  }
  body.departmentId = fields.departmentId;
  body.departmentName = fields.departmentName;
}

async function openAdd() {
  await Promise.all([loadRoles(), loadAssessmentRules(), ensureDepartmentTree()]);
  editing.value = null;
  formDepartmentFilter.value = "";
  feishuPickKeyword.value = "";
  feishuPickerOpen.value = false;
  const code =
    subjectCodeQueryValue(listFilterSubjectCode.value) ||
    subjectCodeQueryValue(feishuSubjects.value[0]?.code) ||
    "";
  form.value = {
    userId: "",
    name: "",
    feishuSubjectCode: code,
    roleKey: "employee",
    employeeNo: "",
    departmentName: "",
    phone: "",
    assessmentRuleId: "",
  };
  applyDirectoryFromCache(code);
  formDialogError.value = null;
  formOpen.value = true;
}

async function openEdit(row: EmployeeListItem) {
  await Promise.all([loadAssessmentRules(), ensureDepartmentTree()]);
  editing.value = row;
  formDepartmentFilter.value = employeeRowToDepartmentFilter(row, departmentTreeCache.value);
  feishuPickKeyword.value = "";
  feishuPickAllUsers.value = [];
  feishuPickerOpen.value = false;
  form.value = {
    userId: row.userId,
    name: row.name,
    phone: row.phone,
    employeeNo: row.employeeNo,
    departmentName: row.departmentName,
    managerId: row.managerId,
    dottedManagerId: row.dottedManagerId,
    roleKey: row.roleKey,
    feishuSubjectCode: row.feishuSubjectCode,
    feishuSubjectId: row.feishuSubjectId,
    feishuOpenId: row.feishuOpenId,
    feishuSubjectName: row.feishuSubjectName,
    assessmentRuleId: row.assessmentRuleId ?? "",
  };
  formDialogError.value = null;
  formOpen.value = true;
}

function openHierarchy(row: EmployeeListItem) {
  hierarchyTarget.value = row;
  managerId.value = row.managerId || "";
  dottedManagerId.value = row.dottedManagerId || "";
  hierarchyOpen.value = true;
}

async function ensureFeishuDirectoryLoaded() {
  const code =
    subjectCodeQueryValue(form.value.feishuSubjectCode) ||
    subjectCodeQueryValue(listFilterSubjectCode.value) ||
    subjectCodeQueryValue(feishuSubjects.value[0]?.code) ||
    "";
  if (!code) {
    feishuPickAllUsers.value = [];
    message.value = "请先选择飞书主体";
    return;
  }
  if (applyDirectoryFromCache(code)) {
    return;
  }
  feishuPickLoading.value = true;
  try {
    await fetchDirectoryIntoCache(code);
    if (!applyDirectoryFromCache(code)) {
      message.value = "飞书通讯录加载失败";
    }
  } catch (e) {
    feishuPickAllUsers.value = [];
    message.value = e instanceof Error ? e.message : "飞书通讯录加载失败";
  } finally {
    feishuPickLoading.value = false;
  }
}

function onFeishuPickerFocus() {
  feishuPickerOpen.value = true;
  void ensureFeishuDirectoryLoaded();
}

function onFeishuPickerInput() {
  feishuPickerOpen.value = true;
}

function onFeishuPickerBlur() {
  setTimeout(() => {
    feishuPickerOpen.value = false;
  }, 200);
}

function strField(v: unknown): string {
  if (v == null) return "";
  return String(v).trim();
}

async function pickFeishuUser(o: FeishuUserOptionItem) {
  form.value.userId = o.feishuOpenId;
  form.value.feishuOpenId = o.feishuOpenId;
  form.value.name = o.name;
  form.value.feishuSubjectCode = o.feishuSubjectCode;
  form.value.employeeNo = strField(o.employeeNo);
  form.value.departmentName = strField(o.departmentName);
  form.value.phone = "";
  try {
    const profRaw = (await getFeishuUserProfile({
      subjectCode: o.feishuSubjectCode,
      openId: o.feishuOpenId,
    })) as unknown as Record<string, unknown>;
    const no = strField(profRaw.employeeNo ?? profRaw.employee_no);
    const dn = strField(profRaw.departmentName ?? profRaw.department_name);
    const mob = strField(profRaw.mobile ?? profRaw.mobile_phone);
    if (no) form.value.employeeNo = no;
    if (dn) form.value.departmentName = dn;
    if (mob) form.value.phone = mob;
    if (dn) {
      await ensureDepartmentTree();
      const matched = matchDepartmentFilterByName(o.feishuSubjectCode, dn, departmentTreeCache.value);
      if (matched) formDepartmentFilter.value = matched;
    }
  } catch {
    /* 列表已带部分字段，详情失败不阻断选人 */
  }
  const ex = employees.value.find(
    (e) => e.userId === o.feishuOpenId || (e.feishuOpenId && e.feishuOpenId === o.feishuOpenId),
  );
  if (ex) {
    if (!strField(form.value.employeeNo) && strField(ex.employeeNo)) form.value.employeeNo = strField(ex.employeeNo);
    if (!formDepartmentFilter.value && (ex.departmentName || ex.departmentId)) {
      formDepartmentFilter.value = employeeRowToDepartmentFilter(ex, departmentTreeCache.value);
    }
  }
  feishuPickKeyword.value = o.name;
  feishuPickerOpen.value = false;
}

async function saveEmployee() {
  try {
    await ensureDepartmentTree();
    if (editing.value) {
      const { userId: _userId, feishuSubjectName: _fsn, ...body } = form.value;
      applyFormDepartmentToBody(body as UpdateEmployeeRequest);
      await updateEmployee(editing.value.userId, body as UpdateEmployeeRequest);
    } else {
      const body = { ...form.value };
      applyFormDepartmentToBody(body);
      await createEmployee(body);
    }
    formDialogError.value = null;
    formOpen.value = false;
    await load();
  } catch (e) {
    formDialogError.value = e instanceof Error ? e.message : "保存失败";
  }
}

async function saveHierarchy() {
  if (!hierarchyTarget.value) return;
  try {
    await updateEmployeeHierarchy(hierarchyTarget.value.userId, {
      managerId: managerId.value || undefined,
      dottedManagerId: dottedManagerId.value || undefined,
    });
    hierarchyOpen.value = false;
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "保存失败";
  }
}

async function remove(row: EmployeeListItem) {
  if (!window.confirm(`确定删除员工「${row.name}」？`)) return;
  try {
    await deleteEmployee(row.userId);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => {
  void load();
  void loadRoles();
  void loadFeishuSubjects();
  void loadAssessmentRules();
  void loadCalibrationPanel();
});

watch([page, pageSize], () => void load());

function onListFilterSubjectChange(ev: Event) {
  const el = ev.target as HTMLSelectElement | null;
  if (!el) return;
  listFilterSubjectCode.value = el.value;
  page.value = 1;
  void load();
}

watch(
  () => form.value.feishuSubjectCode,
  () => {
    if (!formOpen.value) return;
    formDepartmentFilter.value = "";
    if (editing.value) return;
    const code = (form.value.feishuSubjectCode || "").trim();
    if (!applyDirectoryFromCache(code)) {
      feishuPickAllUsers.value = [];
      feishuPickDirectoryTotal.value = 0;
      feishuPickDirectoryTruncated.value = false;
    }
    if (feishuPickerOpen.value) {
      void ensureFeishuDirectoryLoaded();
    }
  },
);

watch(feishuSubjects, (subs) => {
  if (subs.length) prefetchAllFeishuDirectories();
});

watch(
  feishuDirectoryCache,
  () => {
    if (!formOpen.value || editing.value) return;
    const code = (form.value.feishuSubjectCode || "").trim();
    if (code && feishuDirectoryCache.value[code]) {
      applyDirectoryFromCache(code);
    }
  },
  { deep: true },
);
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex flex-wrap items-center gap-2">
        <button type="button" class="rounded-md border px-3 py-2 text-sm" @click="importOpen = true">导入花名册</button>
        <button type="button" class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground" @click="openAdd">
          新增员工
        </button>
      </div>
    </div>
    <p v-if="message && !formOpen" class="text-sm text-muted-foreground">{{ message }}</p>
    <section class="ui-card flex flex-wrap items-center justify-between gap-3 p-6">
      <div>
        <h2 class="text-base font-semibold">绩效校准负责人</h2>
        <p class="mt-1 text-sm text-muted-foreground">
          绩效详情流程「绩效校准」节点显示所选姓名；未选择时显示「管理员」。
        </p>
        <p v-if="calibrationLoading" class="mt-2 text-sm text-muted-foreground">加载中…</p>
        <p v-else class="mt-2 text-sm text-foreground">{{ calibrationSummary }}</p>
      </div>
      <button
        type="button"
        class="shrink-0 rounded-md border border-border bg-card px-4 py-2 text-sm hover:bg-accent"
        :disabled="calibrationLoading"
        @click="openCalibrationDialog"
      >
        选择负责人</button>
    </section>

    <div
      v-if="calibrationOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="closeCalibrationDialog"
    >
      <div class="flex max-h-[min(32rem,85vh)] w-full max-w-lg flex-col rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">选择绩效校准负责人</h2>
        <p class="mt-1 text-sm text-muted-foreground">可多选；保存后生效于绩效详情流程节点。</p>
        <div v-if="calibrationLoading" class="ui-loading mt-4">加载中…</div>
        <div v-else class="mt-4 min-h-0 flex-1 overflow-y-auto rounded-md border border-border p-3 text-sm">
          <p v-if="!calibrationDirectory.length" class="text-muted-foreground">暂无员工档案，请先维护员工。</p>
          <template v-else>
            <label
              v-for="opt in calibrationDirectory"
              :key="opt.userId"
              class="flex cursor-pointer items-center gap-2 py-1.5 hover:bg-accent"
            >
              <input
                type="checkbox"
                class="h-4 w-4 shrink-0 rounded border"
                :checked="calibrationDraftIdsSet.has(opt.userId)"
                @change="onCalibrationDraftToggle(opt.userId, ($event.target as HTMLInputElement).checked)"
              />
              <span>{{ opt.name || opt.userId }}</span>
              <span v-if="opt.departmentName" class="text-xs text-muted-foreground">{{ opt.departmentName }}</span>
            </label>
          </template>
        </div>
        <div class="mt-6 flex shrink-0 justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="closeCalibrationDialog">取消</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
            :disabled="calibrationSaving || calibrationLoading"
            @click="saveCalibrationAssignees"
          >
            {{ calibrationSaving ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>
    <div class="flex flex-wrap items-end gap-3">
      <div v-if="feishuSubjects.length" class="flex flex-col gap-1">
        <label class="text-sm text-muted-foreground">飞书主体</label>
        <select
          class="min-w-[10rem] rounded-md border px-3 py-2 text-sm"
          :value="listFilterSubjectCode"
          @change="onListFilterSubjectChange"
        >
          <option value="">全部</option>
          <option v-for="s in feishuSubjects" :key="s.code" :value="s.code">{{ s.name }}（{{ s.code }}）</option>
        </select>
      </div>
      <div class="flex flex-col gap-1">
        <label class="ui-label">部门</label>
        <DepartmentTreeSelect v-model="departmentFilter" />
      </div>
      <div class="flex flex-col gap-1">
        <label class="text-sm text-muted-foreground">关键词</label>
        <input
          v-model="keyword"
          class="w-64 rounded-md border px-3 py-2 text-sm"
          placeholder="搜索姓名/工号"
          @keyup.enter="page = 1; load()"
        />
      </div>
      <button type="button" class="rounded-md border px-3 py-2 text-sm" @click="page = 1; load()">搜索</button>
    </div>
    <section class="ui-list-panel">
      <div v-if="loading" class="ui-loading">加载中…</div>
      <div v-else class="ui-table-wrap">
        <table class="ui-table min-w-[900px]">
          <thead>
            <tr>
              <th>飞书主体</th>
              <th>姓名</th>
              <th>工号</th>
              <th>部门</th>
              <th>手机号</th>
              <th>上级</th>
              <th>虚线上级</th>
              <th>考核规则</th>
              <th>角色</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in employees" :key="row.userId">
              <td>{{ row.feishuSubjectName || row.feishuSubjectCode || "—" }}</td>
              <td>
                <UserDisplay size="small" :value="{ user_id: row.userId, name: row.name }" />
              </td>
              <td>{{ row.employeeNo || row.userId }}</td>
              <td>{{ row.departmentName || "—" }}</td>
              <td>{{ row.phone || "—" }}</td>
              <td>
                <UserDisplay
                  v-if="row.managerId"
                  size="small"
                  :value="{ user_id: row.managerId, name: row.managerName || row.managerId }"
                />
                <span v-else>—</span>
              </td>
              <td>
                <UserDisplay
                  v-if="row.dottedManagerId"
                  size="small"
                  :value="{ user_id: row.dottedManagerId, name: row.dottedManagerName || row.dottedManagerId }"
                />
                <span v-else>—</span>
              </td>
              <td>{{ row.assessmentRuleName || "—" }}</td>
              <td>{{ row.roleName || row.roleKey || "—" }}</td>
              <td class="text-right text-base">
                <button type="button" class="mr-2 text-primary hover:underline" @click="openHierarchy(row)">层级</button>
                <button type="button" class="mr-2 text-primary hover:underline" @click="openEdit(row)">编辑</button>
                <button type="button" class="text-destructive hover:underline" @click="remove(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </section>

    <div v-if="formOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="formOpen = false">
      <div class="w-full max-w-lg rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">{{ editing ? "编辑员工" : "新增员工" }}</h2>
        <div class="mt-4 space-y-3">
          <template v-if="!editing">
            <div v-if="feishuSubjects.length">
              <label class="mb-1 block text-sm text-muted-foreground">飞书主体</label>
              <select v-model="form.feishuSubjectCode" class="w-full rounded-md border px-3 py-2 text-sm">
                <option v-for="s in feishuSubjects" :key="s.code" :value="s.code">{{ s.name }}（{{ s.code }}）</option>
              </select>
            </div>
            <div class="relative">
              <label class="mb-1 block text-sm text-muted-foreground">从飞书选人（可选）</label>
              <input
                v-model="feishuPickKeyword"
                type="search"
                class="ui-input w-full"
                autocomplete="off"
                @focus="onFeishuPickerFocus"
                @blur="onFeishuPickerBlur"
                @input="onFeishuPickerInput"
                @keydown.escape.prevent="feishuPickerOpen = false"
              />
              <ul
                v-if="feishuPickerOpen"
                class="absolute z-50 mt-1 max-h-48 w-full overflow-y-auto rounded-md border border-border bg-card py-1 text-sm shadow-md"
                @mousedown.prevent
              >
                <li v-if="feishuPickLoading" class="px-3 py-2 text-muted-foreground">加载通讯录…</li>
                <li v-else-if="!feishuPickAllUsers.length" class="px-3 py-2 text-muted-foreground">暂无通讯录数据</li>
                <li v-else-if="!feishuPickFilteredDisplay.length" class="px-3 py-2 text-muted-foreground">无匹配，请调整关键词</li>
                <template v-else>
                  <li
                    v-for="o in feishuPickFilteredDisplay"
                    :key="o.feishuOpenId"
                    class="cursor-pointer px-3 py-2 hover:bg-accent"
                    @click="pickFeishuUser(o)"
                  >
                    {{ o.name }} <span class="text-muted-foreground">· {{ o.feishuOpenId }}</span>
                  </li>
                  <li
                    v-if="feishuPickDisplayHasMore"
                    class="border-t border-border px-3 py-2 text-xs text-muted-foreground"
                  >
                    当前列表共 {{ feishuPickFiltered.length }} 条匹配，以下最多展示 {{ FEISHU_PICK_DISPLAY_CAP }} 条，请输入关键词缩小范围
                  </li>
                  <li
                    v-else-if="feishuPickDirectoryTruncated || feishuPickDirectoryTotal > feishuPickAllUsers.length"
                    class="border-t border-border px-3 py-2 text-xs text-muted-foreground"
                  >
                    通讯录约 {{ feishuPickDirectoryTotal }} 人{{ feishuPickDirectoryTruncated ? "（接口仅返回前若干条）" : "" }}
                  </li>
                </template>
              </ul>
            </div>
          </template>
          <template v-else>
            <p v-if="form.feishuSubjectName || form.feishuSubjectCode" class="text-xs text-muted-foreground">
              飞书主体：{{ form.feishuSubjectName || form.feishuSubjectCode }}
            </p>
            <div>
              <label class="mb-1 block text-sm">飞书 open_id（可选修改）</label>
              <input v-model="form.feishuOpenId" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="feishu_open_id" />
            </div>
          </template>
          <input v-if="!editing" v-model="form.userId" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="userId（可与飞书 open_id 一致）" />
          <input v-model="form.name" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="姓名" />
          <input v-model="form.employeeNo" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="工号" />
          <input v-model="form.phone" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="手机号" />
          <div>
            <label class="mb-1 block text-sm">部门</label>
            <DepartmentTreeSelect
              v-model="formDepartmentFilter"
              :subject-code="form.feishuSubjectCode || ''"
              dept-only
              :show-clear-option="false"
              placeholder="请选择部门"
            />
          </div>
          <div>
            <label class="mb-1 block text-sm text-muted-foreground">考核规则</label>
            <select v-model="form.assessmentRuleId" class="w-full rounded-md border px-3 py-2 text-sm">
              <option value="">（不绑定）</option>
              <option v-for="r in assessmentRuleOptions" :key="r.id" :value="r.id">
                {{ r.name }}（直属 {{ Math.round(r.managerWeight * 100) }}% / 虚线 {{ Math.round(r.dottedManagerWeight * 100) }}%）
              </option>
            </select>
          </div>
          <select v-model="form.roleKey" class="w-full rounded-md border px-3 py-2 text-sm">
            <option v-if="editing" value="">（未分配系统角色）</option>
            <option v-for="r in roleOptions" :key="r.roleKey" :value="r.roleKey">{{ r.name }}</option>
          </select>
        </div>
        <p v-if="formDialogError" class="mt-3 text-sm text-destructive">{{ formDialogError }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="formOpen = false">取消</button>
          <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="saveEmployee">
            保存
          </button>
        </div>
      </div>
    </div>

    <div
      v-if="hierarchyOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="hierarchyOpen = false"
    >
      <div class="w-full max-w-md rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">设置上级 — {{ hierarchyTarget?.name }}</h2>
        <div class="mt-4 space-y-3">
          <div>
            <label class="mb-1 block text-sm">直属上级</label>
            <EmployeeSupervisorSelect
              v-model="managerId"
              :exclude-user-ids="[hierarchyTarget?.userId || '', dottedManagerId].filter(Boolean)"
              :selected-name="hierarchyTarget?.managerName"
              placeholder="选择直属上级"
            />
          </div>
          <div>
            <label class="mb-1 block text-sm">虚线上级</label>
            <EmployeeSupervisorSelect
              v-model="dottedManagerId"
              :exclude-user-ids="[hierarchyTarget?.userId || '', managerId].filter(Boolean)"
              :selected-name="hierarchyTarget?.dottedManagerName"
              placeholder="选择虚线上级"
            />
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="hierarchyOpen = false">取消</button>
          <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="saveHierarchy">
            保存
          </button>
        </div>
      </div>
    </div>

    <ExcelImportDialog
      :open="importOpen"
      :subjects="feishuSubjects"
      :default-subject-code="listFilterSubjectCode"
      @close="importOpen = false"
      @success="load"
    />
  </div>
</template>
