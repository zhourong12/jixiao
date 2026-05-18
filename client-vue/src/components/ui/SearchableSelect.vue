<script setup lang="ts">
import { computed, ref, watch } from "vue";

export type SearchableSelectOption = {
  value: string;
  label: string;
  description?: string;
};

const model = defineModel<string>({ default: "" });

const props = withDefaults(
  defineProps<{
    options: SearchableSelectOption[];
    placeholder?: string;
    disabled?: boolean;
    loading?: boolean;
    emptyText?: string;
  }>(),
  {
    placeholder: "请选择",
    disabled: false,
    loading: false,
    emptyText: "没有匹配项，换个关键词试试",
  },
);

const emit = defineEmits<{
  change: [];
}>();

const keyword = ref("");
const dropdownOpen = ref(false);
const inputRef = ref<HTMLInputElement | null>(null);

function labelFor(value: string) {
  return props.options.find((item) => item.value === value)?.label ?? "";
}

function syncFromModel() {
  keyword.value = model.value ? labelFor(model.value) : "";
}

const filteredOptions = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  if (!query) return props.options;
  return props.options.filter((item) => {
    const haystack = [item.label, item.description, item.value].filter(Boolean).join(" ").toLowerCase();
    return haystack.includes(query);
  });
});

function openDropdown() {
  dropdownOpen.value = true;
  requestAnimationFrame(() => inputRef.value?.select());
}

function closeDropdown() {
  dropdownOpen.value = false;
  syncFromModel();
}

function pick(value: string) {
  model.value = value;
  keyword.value = value ? labelFor(value) : "";
  dropdownOpen.value = false;
  emit("change");
}

function onInput() {
  if (model.value && keyword.value.trim() !== labelFor(model.value).trim()) {
    model.value = "";
  }
  dropdownOpen.value = true;
}

function clearSelection() {
  model.value = "";
  keyword.value = "";
  dropdownOpen.value = true;
  inputRef.value?.focus();
}

watch(
  () => [model.value, props.options] as const,
  () => {
    if (!dropdownOpen.value) syncFromModel();
  },
  { deep: true },
);
</script>

<template>
  <div class="relative">
    <input
      ref="inputRef"
      v-model="keyword"
      type="text"
      class="ui-input pr-9"
      :placeholder="loading ? '加载中…' : placeholder"
      :disabled="disabled || loading"
      autocomplete="off"
      @focus="openDropdown"
      @blur="closeDropdown"
      @input="onInput"
      @keydown.escape.prevent="dropdownOpen = false"
    />
    <button
      v-if="model"
      type="button"
      class="absolute right-2 top-1/2 -translate-y-1/2 rounded-sm px-1 text-muted-foreground hover:text-foreground"
      :disabled="disabled || loading"
      aria-label="清空"
      @mousedown.prevent
      @click="clearSelection"
    >
      ×
    </button>
    <ul
      v-if="dropdownOpen"
      class="absolute z-50 mt-1 max-h-56 w-full overflow-auto rounded-md border border-border bg-card py-1 shadow-md"
      @mousedown.prevent
    >
      <li>
        <button
          type="button"
          class="w-full px-3 py-2 text-left text-sm text-muted-foreground hover:bg-accent"
          @click="pick('')"
        >
          {{ placeholder }}
        </button>
      </li>
      <li v-if="loading" class="px-3 py-2 text-sm text-muted-foreground">加载中…</li>
      <li v-else-if="filteredOptions.length === 0" class="px-3 py-2 text-sm text-muted-foreground">
        {{ emptyText }}
      </li>
      <li v-for="item in filteredOptions" :key="item.value">
        <button
          type="button"
          class="w-full px-3 py-2 text-left text-sm hover:bg-accent"
          :class="model === item.value ? 'bg-accent font-medium text-accent-foreground' : 'text-foreground'"
          @click="pick(item.value)"
        >
          {{ item.label }}<span v-if="item.description" class="text-muted-foreground">（{{ item.description }}）</span>
        </button>
      </li>
    </ul>
  </div>
</template>
