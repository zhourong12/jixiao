<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import type { FeishuSubjectOption, OrgDepartmentListItem } from "@/types/api.interface";
import { getFeishuLoginSubjects, subjectCodeQueryValue } from "@/api/employees";
import {
  createOrgDepartment,
  deleteOrgDepartment,
  listOrgDepartments,
  syncOrgDepartmentsFromEmployees,
  updateOrgDepartment,
} from "@/api/departments";
import ListPagination from "@/components/ui/ListPagination.vue";

const items = ref<OrgDepartmentListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(true);
const syncing = ref(false);
const message = ref<string | null>(null);
const subjectCode = ref("");
const keyword = ref("");
const feishuSubjects = ref<FeishuSubjectOption[]>([]);

const dialogOpen = ref(false);
const dialogMode = ref<"create" | "edit">("edit");
const editing = ref<OrgDepartmentListItem | null>(null);
const formSubjectCode = ref("");
const formName = ref("");
const formSort = ref(0);
const saving = ref(false);
const dialogError = ref<string | null>(null);
type DialogErrorField = "name" | "subject" | "general";
const dialogErrorField = ref<DialogErrorField | null>(null);

function setDialogError(field: DialogErrorField, msg: string) {
  dialogErrorField.value = field;
  dialogError.value = msg;
}

function clearDialogError() {
  dialogErrorField.value = null;
  dialogError.value = null;
}

async function loadSubjects() {
  try {
    const res = await getFeishuLoginSubjects();
    feishuSubjects.value = res.items ?? [];
  } catch {
    feishuSubjects.value = [];
  }
}

async function load() {
  loading.value = true;
  message.value = null;
  try {
    const res = await listOrgDepartments({
      page: page.value,
      pageSize: pageSize.value,
      subjectCode: subjectCodeQueryValue(subjectCode.value),
      keyword: keyword.value || undefined,
    });
    items.value = res.items;
    total.value = res.total;
  } catch (e) {
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function syncFromEmployees() {
  if (!window.confirm("将从员工档案汇总部门并写入部门主数据（按主体+名称去重），是否继续？")) return;
  syncing.value = true;
  message.value = null;
  try {
    const res = await syncOrgDepartmentsFromEmployees();
    message.value = `已同步 ${res.upserted ?? 0} 条部门`;
    page.value = 1;
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "同步失败";
  } finally {
    syncing.value = false;
  }
}

function search() {
  page.value = 1;
  void load();
}

function openCreate() {
  dialogMode.value = "create";
  editing.value = null;
  formSubjectCode.value =
    subjectCodeQueryValue(subjectCode.value) || feishuSubjects.value[0]?.code || "";
  formName.value = "";
  formSort.value = 0;
  clearDialogError();
  dialogOpen.value = true;
}

function openEdit(row: OrgDepartmentListItem) {
  dialogMode.value = "edit";
  editing.value = row;
  formSubjectCode.value = row.subjectCode;
  formName.value = row.name;
  formSort.value = row.sortOrder ?? 0;
  clearDialogError();
  dialogOpen.value = true;
}

function closeDialog() {
  dialogOpen.value = false;
  editing.value = null;
  clearDialogError();
}

async function save() {
  const name = formName.value.trim();
  clearDialogError();
  if (!name) {
    setDialogError("name", "请填写部门名称");
    return;
  }
  saving.value = true;
  try {
    if (dialogMode.value === "create") {
      const code = subjectCodeQueryValue(formSubjectCode.value);
      if (!code) {
        setDialogError("subject", "请选择飞书主体");
        saving.value = false;
        return;
      }
      await createOrgDepartment({
        subjectCode: code,
        name,
        sortOrder: Number(formSort.value) || 0,
      });
    } else if (editing.value) {
      await updateOrgDepartment(editing.value.id, {
        name,
        sortOrder: Number(formSort.value) || 0,
      });
    }
    closeDialog();
    await load();
  } catch (e) {
    setDialogError("general", e instanceof Error ? e.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function remove(row: OrgDepartmentListItem) {
  const hint =
    row.employeeCount > 0
      ? `部门「${row.name}」下仍有 ${row.employeeCount} 名员工档案，删除后筛选项将不再显示该部门（员工档案中的部门名不变）。确定删除？`
      : `确定删除部门「${row.name}」吗？`;
  if (!window.confirm(hint)) return;
  message.value = null;
  try {
    await deleteOrgDepartment(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => {
  void loadSubjects();
  void load();
});
watch([page, pageSize], () => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold">部门管理</h1>
        <p class="mt-1 text-sm text-muted-foreground">
          按飞书主体维护部门主数据；筛选项与员工/绩效列表共用。
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
        <button type="button" class="ui-btn-primary" @click="openCreate">新增部门</button>
        <button type="button" class="ui-btn-outline" :disabled="syncing" @click="syncFromEmployees">
          {{ syncing ? "同步中…" : "从员工档案同步" }}
        </button>
      </div>
    </div>

    <p v-if="message && !dialogOpen" class="text-sm" :class="message.includes('失败') ? 'text-danger' : 'text-muted-foreground'">
      {{ message }}
    </p>

    <div class="ui-card space-y-4 p-6">
      <div class="flex flex-wrap items-end gap-3">
        <div v-if="feishuSubjects.length" class="flex flex-col gap-1">
          <label class="ui-label">飞书主体</label>
          <select v-model="subjectCode" class="ui-input min-w-[10rem]" @change="search">
            <option value="">全部</option>
            <option v-for="s in feishuSubjects" :key="s.code" :value="s.code">{{ s.name }}</option>
          </select>
        </div>
        <div class="flex flex-col gap-1">
          <label class="ui-label">部门名称</label>
          <input v-model="keyword" class="ui-input w-48" placeholder="关键词" @keyup.enter="search" />
        </div>
        <button type="button" class="ui-btn-primary" @click="search">查询</button>
      </div>

      <section class="ui-list-panel">
        <div v-if="loading" class="ui-loading">加载中…</div>
        <div v-else class="ui-table-wrap">
          <table class="ui-table min-w-[560px]">
            <thead>
              <tr>
                <th class="py-3.5">飞书主体</th>
                <th class="py-3.5">部门名称</th>
                <th class="py-3.5">关联员工数</th>
                <th class="py-3.5 text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="items.length === 0">
                <td colspan="4" class="ui-empty py-8">暂无部门，可新增或从员工档案同步</td>
              </tr>
              <tr v-for="row in items" :key="row.id">
                <td class="py-4 text-muted-foreground">{{ row.subjectName || row.subjectCode }}</td>
                <td class="py-4 font-medium">{{ row.name }}</td>
                <td class="py-4">{{ row.employeeCount }}</td>
                <td class="py-4 text-right space-x-1">
                  <button type="button" class="ui-btn-ghost ui-btn-sm" @click="openEdit(row)">编辑</button>
                  <button type="button" class="ui-btn-ghost ui-btn-sm text-danger" @click="remove(row)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </section>
    </div>

    <div
      v-if="dialogOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="closeDialog"
    >
      <div class="ui-card w-full max-w-md p-6">
        <h2 class="text-lg font-semibold">{{ dialogMode === "create" ? "新增部门" : "编辑部门" }}</h2>
        <label v-if="dialogMode === 'create'" class="mt-4 block">
          <span class="ui-label">飞书主体</span>
          <select v-model="formSubjectCode" class="ui-input mt-1 w-full">
            <option v-if="!feishuSubjects.length" value="">未配置主体</option>
            <option v-for="s in feishuSubjects" :key="s.code" :value="s.code">{{ s.name }}</option>
          </select>
          <p v-if="dialogErrorField === 'subject' && dialogError" class="mt-1 text-xs text-danger">{{ dialogError }}</p>
        </label>
        <p v-else-if="editing" class="mt-2 text-sm text-muted-foreground">{{ editing.subjectName }}</p>
        <label class="mt-3 block">
          <span class="ui-label">部门名称</span>
          <input v-model="formName" class="ui-input mt-1 w-full" />
          <p v-if="dialogErrorField === 'name' && dialogError" class="mt-1 text-xs text-danger">{{ dialogError }}</p>
        </label>
        <p v-if="dialogErrorField === 'general' && dialogError" class="mt-2 text-sm text-danger">{{ dialogError }}</p>
        <label class="mt-3 block">
          <span class="ui-label">排序</span>
          <input v-model.number="formSort" type="number" class="ui-input mt-1 w-full" />
        </label>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="ui-btn-outline" @click="closeDialog">取消</button>
          <button type="button" class="ui-btn-primary" :disabled="saving" @click="save">
            {{ saving ? "保存中…" : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
