<script setup lang="ts">
import { performanceDetailCopy as c } from "@/composables/performanceDetailCopy";
import type { CultureDimensionDef, CultureReviewItem, PerformanceIndicator, ReviewItem } from "@/types/api.interface";
import { reviewItemDimensionKey } from "@/utils/performanceScoringMerge";

const props = defineProps<{
  cultureDimensions: CultureDimensionDef[];
  learningDimensions: PerformanceIndicator[];
  scoringWeights: { performance: number; culture: number; learning: number } | null;
  cultureSelfReview?: CultureReviewItem[];
  learningSelfReview?: ReviewItem[];
  formatScore?: (v?: number | null) => string;
}>();

function fmt(v?: number | null) {
  return props.formatScore ? props.formatScore(v) : v == null || Number.isNaN(v as number) ? "—" : String(v);
}

function cultureRow(name: string) {
  return props.cultureSelfReview?.find((x) => x.name === name);
}

function learningRow(name: string) {
  return props.learningSelfReview?.find((x) => reviewItemDimensionKey(x as ReviewItem & { name?: string }) === name);
}
</script>

<template>
  <!-- 文化价值观（只读；方案占比为 0 时不展示） -->
  <div v-if="(scoringWeights == null || scoringWeights.culture > 0) && cultureDimensions.length" class="mb-6 mt-8">
    <h3 class="mb-3 text-sm font-semibold text-muted-foreground">
      文化价值观<span v-if="scoringWeights">（占比 {{ scoringWeights.culture }}%）</span>（管理员设定，不可修改）
    </h3>
    <div class="space-y-2">
      <div v-for="dim in cultureDimensions" :key="dim.name" class="rounded-md border border-border bg-accent/30 p-3">
        <div class="flex items-center justify-between gap-2">
          <span class="font-medium text-sm">{{ dim.name }}</span>
          <span class="text-xs text-muted-foreground shrink-0">满分 {{ dim.maxScore }}</span>
        </div>
        <p v-if="dim.description" class="mt-1 text-xs text-muted-foreground">{{ dim.description }}</p>
        <template v-if="cultureRow(dim.name)">
          <div class="mt-2 flex flex-wrap items-baseline justify-between gap-2 border-t border-border/60 pt-2 text-sm">
            <span class="text-muted-foreground">{{ c.selfScore }}</span>
            <span class="font-medium tabular-nums">{{ fmt(cultureRow(dim.name)!.score) }}</span>
          </div>
          <p v-if="cultureRow(dim.name)!.comment" class="mt-1 text-xs text-muted-foreground">
            <span class="font-medium text-foreground/80">{{ c.comment }}：</span>{{ cultureRow(dim.name)!.comment }}
          </p>
        </template>
      </div>
    </div>
  </div>

  <!-- 学习与成长（只读；方案占比为 0 时不展示） -->
  <div v-if="(scoringWeights == null || scoringWeights.learning > 0) && learningDimensions.length" class="mb-6">
    <h3 class="mb-3 text-sm font-semibold text-muted-foreground">
      学习与成长<span v-if="scoringWeights">（占比 {{ scoringWeights.learning }}%）</span>（管理员设定，不可修改）
    </h3>
    <div class="space-y-2">
      <div v-for="dim in learningDimensions" :key="dim.name" class="rounded-md border border-border bg-accent/30 p-3">
        <div class="flex items-center justify-between gap-2">
          <span class="font-medium text-sm">{{ dim.name }}</span>
          <div class="flex shrink-0 flex-wrap items-center justify-end gap-2 text-xs text-muted-foreground">
            <span v-if="dim.maxScore">最高分 {{ dim.maxScore }}</span>
            <span>权重 {{ Number(dim.weight).toFixed(1) }}%</span>
          </div>
        </div>
        <p v-if="dim.description" class="mt-1 text-xs text-muted-foreground">{{ dim.description }}</p>
        <template v-if="learningRow(dim.name)">
          <div class="mt-2 flex flex-wrap items-baseline justify-between gap-2 border-t border-border/60 pt-2 text-sm">
            <span class="text-muted-foreground">{{ c.selfScore }}</span>
            <span class="font-medium tabular-nums">{{ fmt(learningRow(dim.name)!.score) }}</span>
          </div>
          <p v-if="learningRow(dim.name)!.comment" class="mt-1 text-xs text-muted-foreground">
            <span class="font-medium text-foreground/80">{{ c.comment }}：</span>{{ learningRow(dim.name)!.comment }}
          </p>
        </template>
      </div>
    </div>
  </div>
</template>
