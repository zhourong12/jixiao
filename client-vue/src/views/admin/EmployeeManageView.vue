<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { AssessmentRuleListItem, EmployeeListItem, FeishuSubjectOption } from "@/types/api.interface";
import {
  batchUpdateEmployeeAssessmentRule,
  deleteEmployee,
  getEmployeeRoleOptions,
  getEmployees,
  subjectCodeQueryValue,
  getFeishuLoginSubjects,
  syncEmployeesFromFeishu,
  updateEmployee,
} from "@/api/employees";
import { listAssessmentRules } from "@/api/assessmentRules";
import UserDisplay from "@/components/business-ui/UserDisplay.vue";
import DepartmentTreeSelect from "@/components/business-ui/DepartmentTreeSelect.vue";
import ListPagination from "@/components/ui/ListPagination.vue";
import { parseDepartmentFilter } from "@/utils/departmentFilter";
import { isDisplayablePersonName } from "@/utils/user";

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
const form = ref({
  userId: "",
  name: "",
  roleKey: "",
  feishuSubjectName: "",
  feishuSubjectCode: "",
  assessmentRuleId: "",
});
const roleOptions = ref<{ roleKey: string; name: string }[]>([]);
const assessmentRuleOptions = ref<AssessmentRuleListItem[]>([]);
const feishuSubjects = ref<FeishuSubjectOption[]>([]);
/** 列表筛选：空字符串表示全部主体 */
const listFilterSubjectCode = ref("");
const departmentFilter = ref("");

const feishuSyncing = ref(false);

const selectedEmployeeIds = ref<string[]>([]);
const batchRuleOpen = ref(false);
const batchRuleId = ref("");
const batchRuleSaving = ref(false);
const batchRuleError = ref<string | null>(null);

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
    selectedEmployeeIds.value = [];
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

async function runFeishuSync() {
  const subjectLabels =
    feishuSubjects.value.length > 0
      ? feishuSubjects.value.map((s) => s.name || s.code).join("、")
      : "全部已配置的飞书主体";
  if (
    !window.confirm(
      `将从飞书通讯录拉取人员，并仅同步已分配飞书席位（subscription_ids 非空）的人员；无席位的不写入。同步范围：${subjectLabels}。是否继续？`,
    )
  ) {
    return;
  }
  feishuSyncing.value = true;
  message.value = null;
  try {
    const res = await syncEmployeesFromFeishu();
    if (!res.success) {
      message.value = res.message || "飞书同步失败";
      return;
    }
    const parts: string[] = [
      `同步完成：新增 ${res.createdCount}，更新 ${res.updatedCount}`,
    ];
    if ((res.deletedCount ?? 0) > 0) {
      parts.push(`去重删除 ${res.deletedCount}`);
    }
    if ((res.reconciledManagerCount ?? 0) > 0) {
      parts.push(`上级关联修正 ${res.reconciledManagerCount}`);
    }
    if ((res.skippedNoSeatCount ?? 0) > 0) {
      parts.push(`无飞书席位跳过 ${res.skippedNoSeatCount}`);
    }
    if (res.failedCount > 0) {
      parts.push(`失败 ${res.failedCount}`);
    }
    const createdNames = res.createdNames ?? [];
    if (createdNames.length > 0) {
      const maxShow = 30;
      const shown = createdNames.slice(0, maxShow);
      const suffix =
        createdNames.length > maxShow ? `等共 ${createdNames.length} 人` : "";
      parts.push(`新增：${shown.join("、")}${suffix}`);
    }
    for (const s of res.subjects ?? []) {
      const label = s.subjectName || s.subjectCode;
      const del = (s.deletedCount ?? 0) > 0 ? `/去重 ${s.deletedCount}` : "";
      parts.push(`${label} 新增 ${s.createdCount}/更新 ${s.updatedCount}${del}`);
    }
    message.value = parts.join("；");
    page.value = 1;
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "飞书同步失败";
  } finally {
    feishuSyncing.value = false;
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

async function openEdit(row: EmployeeListItem) {
  await Promise.all([loadRoles(), loadAssessmentRules()]);
  editing.value = row;
  form.value = {
    userId: row.userId,
    name: row.name,
    roleKey: row.roleKey ?? "",
    feishuSubjectName: row.feishuSubjectName ?? "",
    feishuSubjectCode: row.feishuSubjectCode ?? "",
    assessmentRuleId: row.assessmentRuleId ?? "",
  };
  formDialogError.value = null;
  formOpen.value = true;
}

async function saveEmployee() {
  if (!editing.value) return;
  try {
    await updateEmployee(editing.value.userId, {
      roleKey: form.value.roleKey ?? "",
      assessmentRuleId: form.value.assessmentRuleId ?? "",
    });
    formDialogError.value = null;
    formOpen.value = false;
    await load();
  } catch (e) {
    formDialogError.value = e instanceof Error ? e.message : "保存失败";
  }
}

const allPageChecked = computed({
  get() {
    const rows = employees.value;
    return rows.length > 0 && rows.every((r) => selectedEmployeeIds.value.includes(r.userId));
  },
  set(checked: boolean) {
    if (checked) {
      selectedEmployeeIds.value = employees.value.map((r) => r.userId);
    } else {
      selectedEmployeeIds.value = [];
    }
  },
});

function toggleEmployeeSelected(userId: string, checked: boolean) {
  if (checked) {
    if (!selectedEmployeeIds.value.includes(userId)) {
      selectedEmployeeIds.value = [...selectedEmployeeIds.value, userId];
    }
  } else {
    selectedEmployeeIds.value = selectedEmployeeIds.value.filter((x) => x !== userId);
  }
}

async function openBatchRuleDialog() {
  if (!selectedEmployeeIds.value.length) return;
  await loadAssessmentRules();
  batchRuleId.value = "";
  batchRuleError.value = null;
  batchRuleOpen.value = true;
}

async function confirmBatchRule() {
  if (!selectedEmployeeIds.value.length) return;
  batchRuleSaving.value = true;
  batchRuleError.value = null;
  try {
    const res = await batchUpdateEmployeeAssessmentRule({
      employeeIds: selectedEmployeeIds.value,
      assessmentRuleId: batchRuleId.value,
    });
    batchRuleOpen.value = false;
    message.value = `已更新 ${res.updatedCount} 名员工的考核规则`;
    await load();
  } catch (e) {
    batchRuleError.value = e instanceof Error ? e.message : "批量设置失败";
  } finally {
    batchRuleSaving.value = false;
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
});

watch([page, pageSize], () => void load());

function onListFilterSubjectChange(ev: Event) {
  const el = ev.target as HTMLSelectElement | null;
  if (!el) return;
  listFilterSubjectCode.value = el.value;
  page.value = 1;
  void load();
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center gap-2">
      <button
        type="button"
        class="ui-btn-outline"
        :disabled="feishuSyncing"
        @click="runFeishuSync"
      >
        {{ feishuSyncing ? "飞书同步中…" : "飞书同步" }}
      </button>
      <button
        type="button"
        class="ui-btn-outline"
        :disabled="!selectedEmployeeIds.length"
        @click="openBatchRuleDialog"
      >
        批量设置考核规则{{ selectedEmployeeIds.length ? `（${selectedEmployeeIds.length}）` : "" }}
      </button>
    </div>
    <p v-if="message && !formOpen" class="text-sm text-muted-foreground">{{ message }}</p>
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
          class="w-full md:w-64 rounded-md border px-3 py-2 text-sm"
          placeholder="搜索姓名/工号"
          @keyup.enter="page = 1; load()"
        />
      </div>
      <button type="button" class="rounded-md border px-3 py-2 text-sm" @click="page = 1; load()">搜索</button>
    </div>
    <section class="ui-list-panel">
      <div v-if="loading" class="ui-loading">加载中…</div>
      <template v-else>
        <div class="ui-mobile-cards">
          <div v-for="row in employees" :key="row.userId" class="ui-card flex items-start justify-between gap-3 p-4">
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-semibold">{{ row.name }}</span>
                <span v-if="row.roleName || row.roleKey" class="rounded-full bg-accent px-2 py-0.5 text-xs text-accent-foreground">{{ row.roleName || row.roleKey }}</span>
              </div>
              <p class="mt-1 text-xs text-muted-foreground">
                {{ row.departmentName || "—" }}<span v-if="row.employeeNo?.trim()"> · {{ row.employeeNo.trim() }}</span>
              </p>
            </div>
            <button type="button" class="shrink-0 text-sm text-primary hover:underline" @click="openEdit(row)">编辑</button>
          </div>
        </div>
        <div class="ui-desktop-table">
        <div class="ui-table-wrap">
        <table class="ui-table min-w-[900px]">
          <thead>
            <tr>
              <th class="w-10">
                <input
                  v-model="allPageChecked"
                  type="checkbox"
                  class="h-4 w-4"
                  :disabled="!employees.length"
                  aria-label="全选本页"
                />
              </th>
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
              <td>
                <input
                  type="checkbox"
                  class="h-4 w-4"
                  :checked="selectedEmployeeIds.includes(row.userId)"
                  :aria-label="`选择 ${row.name}`"
                  @change="toggleEmployeeSelected(row.userId, ($event.target as HTMLInputElement).checked)"
                />
              </td>
              <td>{{ row.feishuSubjectName || row.feishuSubjectCode || "—" }}</td>
              <td>
                <UserDisplay size="small" :value="{ user_id: row.userId, name: row.name }" />
              </td>
              <td>{{ row.employeeNo?.trim() || "—" }}</td>
              <td>{{ row.departmentName || "—" }}</td>
              <td>{{ row.phone || "—" }}</td>
              <td>
                <UserDisplay
                  v-if="row.managerId && isDisplayablePersonName(row.managerName, row.managerId)"
                  size="small"
                  :value="{ user_id: row.managerId, name: row.managerName }"
                />
                <span v-else>—</span>
              </td>
              <td>
                <UserDisplay
                  v-if="row.dottedManagerId && isDisplayablePersonName(row.dottedManagerName, row.dottedManagerId)"
                  size="small"
                  :value="{ user_id: row.dottedManagerId, name: row.dottedManagerName }"
                />
                <span v-else>—</span>
              </td>
              <td>{{ row.assessmentRuleName || "—" }}</td>
              <td>{{ row.roleName || row.roleKey || "—" }}</td>
              <td class="text-right text-base">
                <button type="button" class="mr-2 text-primary hover:underline" @click="openEdit(row)">编辑</button>
                <button type="button" class="text-destructive hover:underline" @click="remove(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
        </div>
      </template>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </section>

    <div v-if="formOpen" class="ui-dialog-backdrop" @click.self="formOpen = false">
      <div class="w-full max-w-lg rounded-md border bg-card p-4 shadow-lg md:p-6">
        <h2 class="text-lg font-semibold">编辑员工</h2>
        <div class="mt-4 space-y-3">
          <div class="rounded-md border border-border bg-muted/30 px-3 py-2 text-sm">
            <p class="font-medium text-foreground">{{ form.name }}</p>
            <p v-if="form.feishuSubjectName || form.feishuSubjectCode" class="mt-1 text-muted-foreground">
              飞书主体：{{ form.feishuSubjectName || form.feishuSubjectCode }}
            </p>
          </div>
          <div>
            <label class="mb-1 block text-sm text-muted-foreground">角色</label>
            <select v-model="form.roleKey" class="w-full rounded-md border px-3 py-2 text-sm">
              <option value="">（未分配系统角色）</option>
              <option v-for="r in roleOptions" :key="r.roleKey" :value="r.roleKey">{{ r.name }}</option>
            </select>
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
      v-if="batchRuleOpen"
      class="ui-dialog-backdrop"
      @click.self="batchRuleOpen = false"
    >
      <div class="w-full max-w-md rounded-md border bg-card p-4 shadow-lg md:p-6">
        <h2 class="text-lg font-semibold">批量设置考核规则</h2>
        <p class="mt-2 text-sm text-muted-foreground">已选 {{ selectedEmployeeIds.length }} 名员工</p>
        <div class="mt-4">
          <label class="mb-1 block text-sm text-muted-foreground">考核规则</label>
          <select v-model="batchRuleId" class="w-full rounded-md border px-3 py-2 text-sm">
            <option value="">（不绑定）</option>
            <option v-for="r in assessmentRuleOptions" :key="r.id" :value="r.id">
              {{ r.name }}（直属 {{ Math.round(r.managerWeight * 100) }}% / 虚线 {{ Math.round(r.dottedManagerWeight * 100) }}%）
            </option>
          </select>
        </div>
        <p v-if="batchRuleError" class="mt-3 text-sm text-destructive">{{ batchRuleError }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="batchRuleOpen = false">取消</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
            :disabled="batchRuleSaving"
            @click="confirmBatchRule"
          >
            {{ batchRuleSaving ? "保存中…" : "确认" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
