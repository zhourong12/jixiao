<script setup lang="ts">
import { onMounted, ref } from "vue";
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
  dialogOpen.value = true;
}

function openEdit(row: RbacRoleItem) {
  editing.value = row;
  roleKey.value = row.roleKey;
  name.value = row.name;
  sortOrder.value = String(row.sortOrder);
  dialogOpen.value = true;
}

async function save() {
  const n = name.value.trim();
  if (!n) {
    message.value = "???????";
    return;
  }
  saving.value = true;
  message.value = null;
  try {
    const so = sortOrder.value.trim() ? parseInt(sortOrder.value, 10) : undefined;
    if (editing.value) {
      await updateRbacRole(editing.value.roleKey, {
        name: n,
        ...(so != null && !Number.isNaN(so) ? { sortOrder: so } : {}),
      });
    } else {
      const k = roleKey.value.trim();
      if (!k) {
        message.value = "???????";
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
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  } finally {
    saving.value = false;
  }
}

async function remove(row: RbacRoleItem) {
  if (!window.confirm(`?????? ${row.name} ?`)) return;
  try {
    await deleteRbacRole(row.roleKey);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold">????</h1>
        <p class="mt-1 text-sm text-muted-foreground">?? RBAC ???</p>
      </div>
      <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="openCreate">
        ????
      </button>
    </div>
    <p v-if="message" class="text-sm text-destructive">{{ message }}</p>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">???...</div>
    <div v-else class="overflow-x-auto rounded-md border border-border">
      <table class="w-full min-w-[640px] text-left text-sm">
        <thead class="border-b bg-muted/40">
          <tr>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2">????</th>
            <th class="px-3 py-2" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.roleKey" class="border-b hover:bg-accent/40">
            <td class="px-3 py-2 font-mono text-xs">{{ row.roleKey }}</td>
            <td class="px-3 py-2">{{ row.name }}</td>
            <td class="px-3 py-2">{{ row.sortOrder }}</td>
            <td class="px-3 py-2">{{ row.isSystem ? "?" : "?" }}</td>
            <td class="px-3 py-2 text-right">
              <button type="button" class="mr-2 text-primary hover:underline" @click="openEdit(row)">??</button>
              <button
                v-if="!row.isSystem"
                type="button"
                class="text-destructive hover:underline"
                @click="remove(row)"
              >
                ??
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div
      v-if="dialogOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="dialogOpen = false"
    >
      <div class="w-full max-w-md rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">{{ editing ? "????" : "????" }}</h2>
        <div class="mt-4 space-y-3">
          <input v-if="!editing" v-model="roleKey" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="roleKey" />
          <input v-model="name" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="??" />
          <input v-model="sortOrder" type="number" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="??" />
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="dialogOpen = false">??</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
            :disabled="saving"
            @click="save"
          >
            {{ saving ? "???..." : "??" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
