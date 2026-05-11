<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { CreateTemplateRequest, PerformanceIndicator, TemplateListItem } from "@/types/api.interface";
import {
  copyTemplate,
  createTemplate,
  deleteTemplate,
  listTemplates,
  toggleTemplateStatus,
} from "@/api/templates";

const items = ref<TemplateListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const loading = ref(true);
const message = ref<string | null>(null);
const dialogOpen = ref(false);
const formName = ref("");
const formPosition = ref("");
const indicators = ref<PerformanceIndicator[]>([{ name: "", weight: 10, description: "" }]);
const saving = ref(false);

async function load() {
  loading.value = true;
  try {
    const res = await listTemplates(page.value, pageSize);
    items.value = res.items;
    total.value = res.total;
  } catch (e) {
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "????";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  formName.value = "";
  formPosition.value = "";
  indicators.value = [{ name: "", weight: 10, description: "" }];
  dialogOpen.value = true;
}

function addIndicator() {
  indicators.value.push({ name: "", weight: 10, description: "" });
}

async function saveTemplate() {
  const body: CreateTemplateRequest = {
    name: formName.value.trim(),
    position: formPosition.value.trim(),
    indicators: indicators.value.filter((i) => i.name.trim()),
  };
  if (!body.name || !body.position || body.indicators.length === 0) {
    message.value = "???????????????????";
    return;
  }
  saving.value = true;
  try {
    await createTemplate(body);
    dialogOpen.value = false;
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  } finally {
    saving.value = false;
  }
}

async function toggle(row: TemplateListItem) {
  try {
    await toggleTemplateStatus(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  }
}

async function copy(row: TemplateListItem) {
  try {
    await copyTemplate(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  }
}

async function remove(row: TemplateListItem) {
  if (!window.confirm(`???????${row.name}??`)) return;
  try {
    await deleteTemplate(row.id);
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
      <h1 class="text-2xl font-bold">????</h1>
      <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="openCreate">
        ????
      </button>
    </div>
    <p v-if="message" class="text-sm text-destructive">{{ message }}</p>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">????</div>
    <div v-else class="overflow-x-auto rounded-md border border-border">
      <table class="w-full min-w-[720px] text-left text-sm">
        <thead class="border-b bg-muted/40">
          <tr>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2">???</th>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2">??</th>
            <th class="px-3 py-2" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id" class="border-b hover:bg-accent/40">
            <td class="px-3 py-2">{{ row.name }}</td>
            <td class="px-3 py-2">{{ row.position }}</td>
            <td class="px-3 py-2">{{ row.indicatorCount }}</td>
            <td class="px-3 py-2">{{ row.status === "enabled" ? "??" : "??" }}</td>
            <td class="px-3 py-2">{{ row.version }}</td>
            <td class="px-3 py-2 text-right">
              <button type="button" class="mr-2 text-primary hover:underline" @click="toggle(row)">????</button>
              <button type="button" class="mr-2 text-primary hover:underline" @click="copy(row)">??</button>
              <button type="button" class="text-destructive hover:underline" @click="remove(row)">??</button>
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
      <div class="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">????</h2>
        <div class="mt-4 space-y-3">
          <input v-model="formName" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="????" />
          <input v-model="formPosition" class="w-full rounded-md border px-3 py-2 text-sm" placeholder="????" />
          <div v-for="(ind, idx) in indicators" :key="idx" class="grid grid-cols-3 gap-2">
            <input v-model="ind.name" class="col-span-2 rounded-md border px-2 py-1 text-sm" placeholder="???" />
            <input v-model.number="ind.weight" type="number" class="rounded-md border px-2 py-1 text-sm" placeholder="??" />
          </div>
          <button type="button" class="text-sm text-primary" @click="addIndicator">+ ????</button>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="dialogOpen = false">??</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
            :disabled="saving"
            @click="saveTemplate"
          >
            {{ saving ? "????" : "??" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
