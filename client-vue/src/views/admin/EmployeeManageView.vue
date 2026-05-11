<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { CreateEmployeeRequest, EmployeeListItem, UpdateEmployeeRequest } from "@/types/api.interface";
import {
  createEmployee,
  deleteEmployee,
  getEmployeeRoleOptions,
  getEmployees,
  syncEmployeesFromLark,
  updateEmployee,
  updateEmployeeHierarchy,
} from "@/api/employees";

const PAGE_SIZE = 10;
const employees = ref<EmployeeListItem[]>([]);
const total = ref(0);
const page = ref(1);
const keyword = ref("");
const loading = ref(false);
const syncing = ref(false);
const message = ref<string | null>(null);

const formOpen = ref(false);
const editing = ref<EmployeeListItem | null>(null);
const form = ref<CreateEmployeeRequest>({ userId: "", name: "" });
const roleOptions = ref<{ roleKey: string; name: string }[]>([]);

const hierarchyOpen = ref(false);
const hierarchyTarget = ref<EmployeeListItem | null>(null);
const managerId = ref("");
const dottedManagerId = ref("");

async function load() {
  loading.value = true;
  try {
    const result = await getEmployees({ page: page.value, pageSize: PAGE_SIZE, keyword: keyword.value || undefined });
    employees.value = result.items;
    total.value = result.total;
  } catch (e) {
    employees.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
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

function openAdd() {
  editing.value = null;
  form.value = { userId: "", name: "" };
  formOpen.value = true;
}

function openEdit(row: EmployeeListItem) {
  editing.value = row;
  form.value = {
    userId: row.userId,
    name: row.name,
    phone: row.phone,
    employeeNo: row.employeeNo,
    departmentName: row.departmentName,
    managerId: row.managerId,
    dottedManagerId: row.dottedManagerId,
    roleKey: row.roleKey,
  };
  formOpen.value = true;
}

function openHierarchy(row: EmployeeListItem) {
  hierarchyTarget.value = row;
  managerId.value = row.managerId || "";
  dottedManagerId.value = row.dottedManagerId || "";
  hierarchyOpen.value = true;
}

async function saveEmployee() {
  try {
    if (editing.value) {
      const { userId: _userId, ...body } = form.value;
      await updateEmployee(editing.value.userId, body as UpdateEmployeeRequest);
    } else {
      await createEmployee(form.value);
    }
    formOpen.value = false;
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "保存失败";
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

async function syncLark() {
  const clearExisting = window.confirm("确定清空现有数据后再同步？取消则增量同步。");
  syncing.value = true;
  try {
    const result = await syncEmployeesFromLark(clearExisting);
    message.value = result.message || (result.success ? `已同步 ${result.syncedCount} 人` : "同步失败");
    if (result.success) await load();
  } finally {
    syncing.value = false;
  }
}

onMounted(() => {
  void load();
  void loadRoles();
});
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <h1 class="text-2xl font-bold">员工管理</h1>
      <div class="flex flex-wrap gap-2">
        <button type="button" class="rounded-md border px-3 py-2 text-sm" :disabled="syncing" @click="syncLark">
          {{ syncing ? "同步中…" : "同步飞书" }}
        </button>
        <button type="button" class="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground" @click="openAdd">
          新增员工
        </button>
      </div>
    </div>
    <p v-if="message" class="text-sm text-muted-foreground">{{ message }}</p>
    <div class="flex gap-2">
      <input
        v-model="keyword"
        class="w-64 rounded-md border px-3 py-2 text-sm"
        placeholder="搜索姓名/编号"
        @keyup.enter="page = 1; load()"
      />
      <button type="button" class="rounded-md border px-3 py-2 text-sm" @click="page = 1; load()">搜索</button>
    </div>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">加载中…</div>
    <div v-else class="overflow-x-auto rounded-md border border-border">
      <table class="w-full min-w-[900px] text-left text-sm">
        <thead class="border-b bg-muted/40">
          <tr>
            <th class="px-3 py-2">姓名</th>
            <th class="px-3 py-2">编号</th>
            <th class="px-3 py-2">部门</th>
            <th class="px-3 py-2">上级</th>
            <th class="px-3 py-2">角色</th>
            <th class="px-3 py-2" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in employees" :key="row.userId" class="border-b hover:bg-accent/40">
            <td class="px-3 py-2">{{ row.name }}</td>
            <td class="px-3 py-2">{{ row.employeeNo || row.userId }}</td>
            <td class="px-3 py-2">{{ row.departmentName || "—" }}</td>
            <td class="px-3 py-2">{{ row.managerName || row.managerId || "—" }}</td>
            <td class="px-3 py-2">{{ row.roleName || row.roleKey || "—" }}</td>
            <td class="px-3 py-2 text-right">
              <button type="button" class="mr-2 text-primary hover:underline" @click="openHierarchy(row)">层级</button>
              <button type="button" class="mr-2 text-primary hover:underline" @click="openEdit(row)">编辑</button>
              <button type="button" class="text-destructive hover:underline" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="total > PAGE_SIZE" class="flex gap-2">
      <button type="button" class="rounded-md border px-3 py-1 text-sm" :disabled="page <= 1" @click="page--; load()">
        上一页
      </button>
      <button
        type="button"
        class="rounded-md border px-3 py-1 text-sm"
        :disabled="page * PAGE_SIZE >= total"
        @click="page++; load()"
      >
        下一页
      </button>
    </div>

    <div v-if="formOpen" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="formOpen = false">
      <div class="w-full max-w-lg rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">{{ editing ? "编辑员工" : "新增员工" }}</h2>
        <div class="mt-4 space-y-3">
          <input v-if="!editing" v-model="form.userId" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="userId" />
          <input v-model="form.name" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="姓名" />
          <input v-model="form.employeeNo" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="员工编号" />
          <input v-model="form.departmentName" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="部门" />
          <select v-model="form.roleKey" class="w-full rounded-md border px-3 py-2 text-sm">
            <option value="">默认员工</option>
            <option v-for="r in roleOptions" :key="r.roleKey" :value="r.roleKey">{{ r.name }}</option>
          </select>
        </div>
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
          <input v-model="managerId" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="直属上级 userId" />
          <input v-model="dottedManagerId" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="虚线上级 userId" />
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="hierarchyOpen = false">取消</button>
          <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="saveHierarchy">
            保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
