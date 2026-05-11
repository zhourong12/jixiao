<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { PerformanceRecord, TemplateListItem } from "@/types/api.interface";
import { getPerformanceDetail, selectTemplate } from "@/api/performances";
import { getTemplates } from "@/api/templates";
import { PERFORMANCE_STATUS_LABELS } from "@/constants/performanceStatus";

const route = useRoute();
const router = useRouter();
const rec = ref<PerformanceRecord | null>(null);
const err = ref<string | null>(null);
const loading = ref(true);
const templates = ref<TemplateListItem[]>([]);
const selecting = ref(false);
const message = ref<string | null>(null);

const statusLabel = computed(() =>
  rec.value ? PERFORMANCE_STATUS_LABELS[rec.value.status] || rec.value.status : "",
);

async function load() {
  const id = route.params.id as string;
  loading.value = true;
  err.value = null;
  try {
    rec.value = await getPerformanceDetail(id);
    if (rec.value.status === "template_selection") {
      templates.value = (await getTemplates()).filter((t) => t.status === "enabled");
    }
  } catch (e) {
    err.value = e instanceof Error ? e.message : "????";
    rec.value = null;
  } finally {
    loading.value = false;
  }
}

async function onSelectTemplate(templateId: string) {
  if (!rec.value) return;
  selecting.value = true;
  message.value = null;
  try {
    await selectTemplate(rec.value.id, { templateId });
    message.value = "?????";
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  } finally {
    selecting.value = false;
  }
}

onMounted(() => void load());
watch(() => route.params.id, () => void load());
</script>

<template>
  <div class="space-y-4">
    <button type="button" class="text-sm text-primary hover:underline" @click="router.push('/performances')">
      ????
    </button>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">????</div>
    <div v-else-if="err" class="rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
      {{ err }}
    </div>
    <template v-else-if="rec">
      <h1 class="text-2xl font-bold">????</h1>
      <p v-if="message" class="text-sm text-muted-foreground">{{ message }}</p>
      <div class="rounded-md border border-border bg-card p-4 text-sm shadow-sm">
        <p><span class="text-muted-foreground">??</span> {{ rec.period }}</p>
        <p class="mt-2"><span class="text-muted-foreground">??</span> {{ statusLabel }}</p>
        <p class="mt-2"><span class="text-muted-foreground">??</span> {{ rec.employeeName || rec.employeeId }}</p>
        <p class="mt-2"><span class="text-muted-foreground">??</span> {{ rec.templateName || rec.templateId || "?" }}</p>
        <p v-if="rec.totalScore != null" class="mt-2">
          <span class="text-muted-foreground">??</span> {{ rec.totalScore }}
        </p>
        <p v-if="rec.rejectionReason" class="mt-2 text-destructive">?????{{ rec.rejectionReason }}</p>
      </div>
      <div v-if="rec.status === 'template_selection'" class="rounded-md border border-border bg-card p-4 shadow-sm">
        <h2 class="mb-3 text-base font-semibold">????</h2>
        <ul class="space-y-2">
          <li
            v-for="t in templates"
            :key="t.id"
            class="flex items-center justify-between rounded-md border px-3 py-2"
          >
            <div>
              <p class="font-medium">{{ t.name }}</p>
              <p class="text-xs text-muted-foreground">{{ t.position }} · {{ t.indicatorCount }} ???</p>
            </div>
            <button
              type="button"
              class="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              :disabled="selecting"
              @click="onSelectTemplate(t.id)"
            >
              ??
            </button>
          </li>
        </ul>
      </div>
      <div v-if="rec.goalSetting?.length" class="rounded-md border border-border bg-card p-4 shadow-sm">
        <h2 class="mb-2 text-base font-semibold">????</h2>
        <ul class="space-y-1 text-sm">
          <li v-for="(g, i) in rec.goalSetting" :key="i">
            {{ g.indicatorName }}??? {{ g.weight }}?{{ g.target ? `?${g.target}` : "" }}
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>
