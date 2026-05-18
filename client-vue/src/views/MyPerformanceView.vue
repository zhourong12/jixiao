<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { PerformanceListItem, PerformanceStatus } from "@/types/api.interface";
import { listPerformances } from "@/api/performances";
import PerformanceStatusBadge from "@/components/ui/PerformanceStatusBadge.vue";
import { formatPeriodDisplay } from "@/utils/period";
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

function actionText(status: PerformanceStatus): string {
  switch (status) {
    case "goal_setting":
      return "去设定目标";
    case "goal_rejected":
      return "去修改目标";
    case "self_review":
      return "去自评";
    case "completed":
      return "查看详情";
    default:
      return "查看进度";
  }
}

onMounted(() => void load());
watch(myUserId, () => void load());
</script>

<template>
  <div class="ui-page">
    <p class="ui-page-intro">查看和管理您的绩效考核</p>
    <div v-if="loading" class="ui-loading">加载中...</div>
    <ul v-else class="space-y-3">
      <li
        v-for="r in records"
        :key="r.id"
        class="ui-card-compact flex cursor-pointer items-center justify-between transition-colors hover:bg-accent"
        @click="router.push(`/performances/${r.id}`)"
      >
        <div class="min-w-0">
          <p class="truncate font-medium text-foreground">{{ formatPeriodDisplay(r.period) }}</p>
          <div class="mt-2">
            <PerformanceStatusBadge :status="r.status" />
          </div>
        </div>
        <span class="shrink-0 text-sm text-primary">{{ actionText(r.status) }} →</span>
      </li>
      <li v-if="records.length === 0" class="ui-empty">
        <p>暂无绩效记录</p>
        <p class="mt-1 text-xs">请联系管理员为您创建绩效考核</p>
      </li>
    </ul>
  </div>
</template>
