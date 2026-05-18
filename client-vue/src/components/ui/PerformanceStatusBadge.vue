<script setup lang="ts">
import type { PerformanceStatus } from "@/types/api.interface";
import {
  normalizePerformanceStatus,
  performanceStatusLabel,
  PERFORMANCE_STATUS_TONES,
  statusToneBadgeClass,
} from "@/constants/performanceStatus";

const props = defineProps<{
  status: PerformanceStatus;
}>();

const normalized = normalizePerformanceStatus(props.status);
const label = performanceStatusLabel(props.status);
const tone = PERFORMANCE_STATUS_TONES[normalized] || "info";
</script>

<template>
  <span class="ui-badge" :class="statusToneBadgeClass(tone)">{{ label }}</span>
</template>
