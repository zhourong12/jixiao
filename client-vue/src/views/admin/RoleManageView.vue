<script setup lang="ts">
import { onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import type { RbacRoleItem } from "@/types/api.interface";
import { createRbacRole, deleteRbacRole, listRbacRoles, updateRbacRole } from "@/api/roles";

const items = ref<RbacRoleItem[]>([]);
const loading = ref(true);
const dialogOpen = ref(false);
const editing = ref<RbacRoleItem | null>(null);
const roleKey = ref("");
const name = ref("");
const sortOrder = ref("");
const saving = ref(false);
const message = ref<string | null>(null);
const dialogError = ref<string | null>(null);
type DialogErrorField = "name" | "roleKey" | "general";
const dialogErrorField = ref<DialogErrorField | null>(null);

function clearDialogError() {
  dialogError.value = null;
  dialogErrorField.value = null;
}

function setDialogError(field: DialogErrorField, msg: string) {
  dialogErrorField.value = field;
  dialogError.value = msg;
}

function notifyMenuPermissionsChanged() {
  window.dispatchEvent(new CustomEvent("menu-permissions-changed"));
}

async function load() {
  loading.value = true;
  try {
    const res = await listRbacRoles();
    items.value = res.items;
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editing.value = null;
  roleKey.value = "";
  name.value = "";
  sortOrder.value = "";
  clearDialogError();
  dialogOpen.value = true;
}

function openEdit(row: RbacRoleItem) {
  editing.value = row;
  roleKey.value = row.roleKey;
  name.value = row.name;
  sortOrder.value = String(row.sortOrder);
  clearDialogError();
  dialogOpen.value = true;
}

async function save() {
  const n = name.value.trim();
  clearDialogError();
  if (!n) {
    setDialogError("name", "请填写名称");
    return;
  }
  saving.value = true;
  try {
    const sortText = String(sortOrder.value ?? "").trim();
    const so = sortText ? parseInt(sortText, 10) : undefined;
    if (editing.value) {
      await updateRbacRole(editing.value.roleKey, {
        name: n,
        ...(so != null && !Number.isNaN(so) ? { sortOrder: so } : {}),
      });
    } else {
      const k = roleKey.value.trim();
      if (!k) {
        setDialogError("roleKey", "请填写 roleKey");
        saving.value = false;
        return;
      }
      await createRbacRole({
        roleKey: k,
        name: n,
        ...(so != null && !Number.isNaN(so) ? { sortOrder: so } : {}),
      });
    }
    dialogOpen.value = false;
    clearDialogError();
    await load();
    notifyMenuPermissionsChanged();
  } catch (e) {
    setDialogError("general", e instanceof Error ? e.message : "操作失败");
  } finally {
    saving.value = false;
  }
}

async function remove(row: RbacRoleItem) {
  if (!window.confirm(`确定删除角色 ${row.name} 吗？`)) return;
  try {
    await deleteRbacRole(row.roleKey);
    await load();
    notifyMenuPermissionsChanged();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <p class="mt-1 text-sm text-muted-foreground">维护 RBAC 角色</p>
      </div>
      <div class="flex items-center gap-3">
        <RouterLink to="/admin/permissions" class="text-sm text-primary hover:underline">权限管理</RouterLink>
        <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="openCreate">
          新建角色
        </button>
      </div>
    </div>
    <p v-if="message && !dialogOpen" class="text-sm text-destructive">{{ message }}</p>
    <section class="ui-list-panel">
      <div v-if="loading" class="ui-loading">加载中...</div>
      <template v-else>
        <div class="ui-mobile-cards">
          <div v-for="row in items" :key="row.roleKey" class="ui-card flex items-start justify-between gap-3 p-4">
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-center gap-2">
                <span class="font-semibold">{{ row.name }}</span>
                <span v-if="row.isSystem" class="rounded-full bg-accent px-2 py-0.5 text-xs text-accent-foreground">系统内置</span>
              </div>
              <p class="mt-1 font-mono text-xs text-muted-foreground">{{ row.roleKey }}</p>
            </div>
            <div class="flex shrink-0 gap-2">
              <button type="button" class="text-sm text-primary hover:underline" @click="openEdit(row)">编辑</button>
              <button v-if="!row.isSystem" type="button" class="text-sm text-destructive hover:underline" @click="remove(row)">删除</button>
            </div>
          </div>
        </div>
        <div class="ui-desktop-table">
        <div class="ui-table-wrap">
        <table class="ui-table min-w-[640px]">
          <thead>
            <tr>
              <th>标识</th>
              <th>名称</th>
              <th>排序</th>
              <th>系统内置</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.roleKey">
              <td class="font-mono text-xs">{{ row.roleKey }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.sortOrder }}</td>
              <td>{{ row.isSystem ? "是" : "否" }}</td>
              <td class="text-right">
                <button type="button" class="mr-2 text-primary hover:underline" @click="openEdit(row)">编辑</button>
                <button
                  v-if="!row.isSystem"
                  type="button"
                  class="text-destructive hover:underline"
                  @click="remove(row)"
                >
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
        </div>
      </template>
    </section>
    <div
      v-if="dialogOpen"
      class="ui-dialog-backdrop"
      @click.self="dialogOpen = false"
    >
      <div class="w-full max-w-md rounded-md border bg-card p-4 shadow-lg md:p-6">
        <h2 class="text-lg font-semibold">{{ editing ? "编辑角色" : "新建角色" }}</h2>
        <div class="mt-4 space-y-3">
          <div v-if="!editing">
            <label class="mb-1 block text-xs font-medium">roleKey</label>
            <input
              v-model="roleKey"
              class="w-full rounded-md border px-3 py-2 text-sm"
              :class="dialogErrorField === 'roleKey' ? 'border-destructive' : ''"
              placeholder="roleKey"
              @input="dialogErrorField === 'roleKey' && clearDialogError()"
            />
            <p v-if="dialogErrorField === 'roleKey' && dialogError" class="mt-1 text-xs text-destructive">{{ dialogError }}</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium">名称</label>
            <input
              v-model="name"
              class="w-full rounded-md border px-3 py-2 text-sm"
              :class="dialogErrorField === 'name' ? 'border-destructive' : ''"
              placeholder="名称"
              @input="dialogErrorField === 'name' && clearDialogError()"
            />
            <p v-if="dialogErrorField === 'name' && dialogError" class="mt-1 text-xs text-destructive">{{ dialogError }}</p>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium">排序</label>
            <input v-model="sortOrder" type="number" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="排序" />
          </div>
        </div>
        <p v-if="dialogErrorField === 'general' && dialogError" class="mt-3 text-sm text-destructive">{{ dialogError }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="dialogOpen = false">取消</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
            :disabled="saving"
            @click="save"
          >
            {{ saving ? "保存中..." : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
