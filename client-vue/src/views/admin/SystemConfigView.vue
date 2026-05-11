<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { getSystemConfig, updateSystemConfig, type SystemConfigItem } from "@/api/systemConfig";

const items = ref<SystemConfigItem[]>([]);
const loading = ref(true);
const saving = ref(false);
const editValues = ref<Record<string, string>>({});
const message = ref<string | null>(null);

const hasChanges = computed(() =>
  items.value.some((item) => (editValues.value[item.key] ?? "") !== item.value),
);

async function load() {
  loading.value = true;
  message.value = null;
  try {
    const data = await getSystemConfig();
    items.value = Array.isArray(data.items) ? data.items : [];
    const vals: Record<string, string> = {};
    for (const item of items.value) vals[item.key] = item.value;
    editValues.value = vals;
  } catch (e) {
    items.value = [];
    message.value = e instanceof Error ? e.message : "load failed";
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  message.value = null;
  try {
    await updateSystemConfig({
      configs: items.value.map((item) => ({
        key: item.key,
        value: editValues.value[item.key] ?? item.value,
      })),
    });
    message.value = "ok";
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "save failed";
  } finally {
    saving.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-foreground">????</h1>
        <p class="mt-1 text-sm text-muted-foreground">?????????????</p>
      </div>
      <button
        type="button"
        class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:opacity-90 disabled:opacity-50"
        :disabled="saving || !hasChanges || items.length === 0"
        @click="save"
      >
        {{ saving ? "???..." : "????" }}
      </button>
    </div>
    <p v-if="message" class="text-sm" :class="message === 'ok' ? 'text-success' : 'text-destructive'">
      {{ message === "ok" ? "?????" : message }}
    </p>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">???...</div>
    <div
      v-else-if="items.length === 0"
      class="rounded-md border border-border bg-card p-8 text-center text-sm text-muted-foreground"
    >
      ??????????
    </div>
    <div v-else class="space-y-4 rounded-md border border-border bg-card p-6 shadow-sm">
      <h2 class="text-base font-semibold">??????</h2>
      <div v-for="item in items" :key="item.key" class="space-y-2">
        <label class="text-sm font-medium">{{ item.label }}</label>
        <p class="text-xs text-muted-foreground">{{ item.description }}</p>
        <input
          v-model="editValues[item.key]"
          :type="item.type === 'number' ? 'number' : 'text'"
          class="w-full max-w-md rounded-md border border-border bg-background px-3 py-2 text-sm"
        />
      </div>
    </div>
  </div>
</template>
