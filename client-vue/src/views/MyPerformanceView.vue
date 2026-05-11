<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { PerformanceListItem } from "@/types/api.interface";
import { listPerformances } from "@/api/performances";
import { PERFORMANCE_STATUS_LABELS } from "@/constants/performanceStatus";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const router = useRouter();
const records = ref<PerformanceListItem[]>([]);
const loading = ref(true);

const myUserId = computed(() => session.userId);

async function load() {
  loading.value = true;
  try {
    if (!myUserId.value) {
      records.value = [];
      return;
    }
    const res = await listPerformances({ page: 1, pageSize: 100 });
    records.value = (res.items || []).filter((i) => i.employeeId === myUserId.value);
  } catch {
    records.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
watch(myUserId, () => void load());
</script>

<template>
  <div class="space-y-4">
    <h1 class="text-2xl font-bold text-foreground">????</h1>
    <div v-if="loading" class="py-16 text-center text-muted-foreground">????</div>
    <ul v-else class="space-y-2">
      <li
        v-for="r in records"
        :key="r.id"
        class="flex cursor-pointer items-center justify-between rounded-md border border-border bg-card p-4 shadow-sm hover:bg-accent"
        @click="router.push(`/performances/${r.id}`)"
      >
        <div>
          <p class="font-medium">{{ r.period }}</p>
          <p class="text-xs text-muted-foreground">{{ PERFORMANCE_STATUS_LABELS[r.status] || r.status }}</p>
        </div>
        <span class="text-primary">?? ?</span>
      </li>
      <li v-if="records.length === 0" class="py-10 text-center text-sm text-muted-foreground">
        ?????????????
      </li>
    </ul>
  </div>
</template>
