<script setup lang="ts">
import { computed, ref } from "vue";

export type SearchableMultiSelectOption = {
  value: string;
  label: string;
};

const model = defineModel<string[]>({ default: () => [] });

const props = withDefaults(
  defineProps<{
    options: SearchableMultiSelectOption[];
    placeholder?: string;
    disabled?: boolean;
    emptyText?: string;
  }>(),
  {
    placeholder: "全部",
    disabled: false,
    emptyText: "没有匹配项",
  },
);

const emit = defineEmits<{
  change: [];
}>();

const keyword = ref("");
const dropdownOpen = ref(false);

const filteredOptions = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  if (!query) return props.options;
  return props.options.filter((item) => {
    const haystack = [item.label, item.value].join(" ").toLowerCase();
    return haystack.includes(query);
  });
});

const summaryText = computed(() => {
  if (!model.value.length) return props.placeholder;
  if (model.value.length === 1) {
    return props.options.find((o) => o.value === model.value[0])?.label ?? model.value[0];
  }
  return `已选 ${model.value.length} 项`;
});

function openDropdown() {
  if (props.disabled) return;
  dropdownOpen.value = true;
}

function closeDropdown() {
  setTimeout(() => {
    dropdownOpen.value = false;
    keyword.value = "";
  }, 200);
}

function toggle(value: string, checked: boolean) {
  if (checked) {
    if (!model.value.includes(value)) {
      model.value = [...model.value, value];
    }
  } else {
    model.value = model.value.filter((v) => v !== value);
  }
  emit("change");
}

function clearAll() {
  model.value = [];
  emit("change");
}
</script>

<template>
  <div class="relative">
    <button
      type="button"
      class="ui-input flex w-full items-center justify-between gap-2 text-left"
      :class="model.length ? 'pr-9' : ''"
      :disabled="disabled"
      @click="openDropdown"
      @blur="closeDropdown"
    >
      <span class="truncate" :class="model.length ? 'text-foreground' : 'text-muted-foreground'">{{ summaryText }}</span>
    </button>
    <button
      v-if="model.length && !disabled"
      type="button"
      class="absolute right-2 top-1/2 -translate-y-1/2 rounded-sm px-1 text-muted-foreground hover:text-foreground"
      aria-label="清空"
      @mousedown.prevent
      @click="clearAll"
    >
      ×
    </button>
    <div
      v-if="dropdownOpen"
      class="absolute z-50 mt-1 w-full overflow-hidden rounded-md border border-border bg-card shadow-md"
      @mousedown.prevent
    >
      <div class="border-b border-border p-2">
        <input
          v-model="keyword"
          type="text"
          class="ui-input w-full"
          placeholder="搜索…"
          autocomplete="off"
          @keydown.escape.prevent="dropdownOpen = false"
        />
      </div>
      <ul class="max-h-56 overflow-auto py-1">
        <li v-if="filteredOptions.length === 0" class="px-3 py-2 text-sm text-muted-foreground">{{ emptyText }}</li>
        <li v-for="item in filteredOptions" :key="item.value">
          <label
            class="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm hover:bg-accent"
            :class="model.includes(item.value) ? 'bg-accent/60' : ''"
          >
            <input
              type="checkbox"
              class="h-4 w-4 shrink-0 rounded border-border"
              :checked="model.includes(item.value)"
              @change="toggle(item.value, ($event.target as HTMLInputElement).checked)"
            />
            <span class="text-foreground">{{ item.label }}</span>
          </label>
        </li>
      </ul>
    </div>
  </div>
</template>
