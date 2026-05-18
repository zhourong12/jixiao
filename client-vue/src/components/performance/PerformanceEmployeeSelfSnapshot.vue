<script setup lang="ts">
import { performanceDetailCopy as c } from "@/composables/performanceDetailCopy";
import type { PerformanceIndicator, ReviewItem } from "@/types/api.interface";

defineProps<{
  indicators: PerformanceIndicator[];
  selfReview?: ReviewItem[];
  personalSummary?: string;
  formatScore: (v?: number | null) => string;
}>();

function rowFor(selfReview: ReviewItem[] | undefined, name: string) {
  return selfReview?.find((x) => x.indicatorName === name);
}
</script>

<template>
  <section class="mb-6 rounded-md border border-border bg-card p-6 shadow-sm">
    <h3 class="mb-4 text-base font-semibold text-foreground">{{ c.selfReview }}（已提交）</h3>
    <div v-if="indicators.length" class="space-y-4">
      <div v-for="ind in indicators" :key="ind.name" class="rounded-md border border-border bg-accent/20 p-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <h4 class="font-medium text-sm">{{ ind.name }}</h4>
          <div class="flex flex-wrap gap-2 text-xs text-muted-foreground">
            <span v-if="ind.maxScore" class="rounded-full border border-border px-2 py-0.5">最高分 {{ ind.maxScore }}</span>
            <span class="rounded-full border border-border px-2 py-0.5">{{ c.weight }} {{ Number(ind.weight).toFixed(1) }}%</span>
          </div>
        </div>
        <p v-if="ind.description" class="mt-1 text-xs text-muted-foreground">{{ ind.description }}</p>
        <div class="mt-3 flex flex-wrap items-baseline justify-between gap-2 border-t border-border/60 pt-3 text-sm">
          <span class="text-muted-foreground">{{ c.selfScore }}</span>
          <span class="font-medium tabular-nums text-foreground">{{ formatScore(rowFor(selfReview, ind.name)?.score) }}</span>
        </div>
        <p v-if="rowFor(selfReview, ind.name)?.comment" class="mt-2 text-xs text-muted-foreground">
          <span class="font-medium text-foreground/80">{{ c.comment }}：</span>{{ rowFor(selfReview, ind.name)!.comment }}
        </p>
      </div>
    </div>
    <div
      v-if="personalSummary && personalSummary.trim()"
      :class="indicators.length ? 'mt-6 border-t border-border pt-4' : ''"
    >
      <h4 class="mb-2 text-sm font-semibold text-muted-foreground">{{ c.summary }}</h4>
      <p class="whitespace-pre-wrap text-sm text-foreground">{{ personalSummary }}</p>
    </div>
  </section>
</template>
