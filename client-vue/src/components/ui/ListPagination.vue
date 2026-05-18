<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    page: number;
    pageSize: number;
    total: number;
    pageSizeOptions?: number[];
  }>(),
  {
    pageSizeOptions: () => [10, 20, 50],
  },
);

const emit = defineEmits<{
  "update:page": [value: number];
  "update:pageSize": [value: number];
}>();

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

const rangeStart = computed(() => {
  if (props.total === 0) return 0;
  return (props.page - 1) * props.pageSize + 1;
});

const rangeEnd = computed(() => Math.min(props.page * props.pageSize, props.total));

const visiblePages = computed(() => {
  const max = totalPages.value;
  const current = props.page;
  const from = Math.max(1, current - 2);
  const to = Math.min(max, current + 2);
  const pages: number[] = [];
  for (let i = from; i <= to; i += 1) pages.push(i);
  return pages;
});

function setPage(next: number) {
  if (next < 1 || next > totalPages.value || next === props.page) return;
  emit("update:page", next);
}

function onPageSizeChange(event: Event) {
  const value = Number.parseInt((event.target as HTMLSelectElement).value, 10);
  if (!Number.isFinite(value) || value <= 0 || value === props.pageSize) return;
  emit("update:pageSize", value);
  emit("update:page", 1);
}
</script>

<template>
  <div v-if="total > 0" class="ui-pagination">
    <span class="ui-pagination-range">第 {{ rangeStart }}-{{ rangeEnd }} 条 / 总共 {{ total }} 条</span>
    <div class="ui-pagination-controls">
      <button type="button" class="ui-pagination-nav" :disabled="page <= 1" @click="setPage(page - 1)">‹</button>
      <button
        v-for="p in visiblePages"
        :key="p"
        type="button"
        class="ui-pagination-page"
        :class="p === page ? 'ui-pagination-page-active' : ''"
        @click="setPage(p)"
      >
        {{ p }}
      </button>
      <button type="button" class="ui-pagination-nav" :disabled="page >= totalPages" @click="setPage(page + 1)">›</button>
    </div>
    <select class="ui-select ui-pagination-size" :value="pageSize" @change="onPageSizeChange">
      <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条/页</option>
    </select>
  </div>
</template>
