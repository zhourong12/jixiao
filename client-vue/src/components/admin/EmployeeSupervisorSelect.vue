<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { EmployeeDirectoryListItem } from "@/types/api.interface";
import { getAllEmployees } from "@/api/employees";

const model = defineModel<string>({ default: "" });

const props = withDefaults(
  defineProps<{
    excludeUserIds?: string[];
    placeholder?: string;
    disabled?: boolean;
    selectedName?: string;
  }>(),
  {
    excludeUserIds: () => [],
    placeholder: "选择员工",
    disabled: false,
    selectedName: "",
  },
);

const keyword = ref("");
const dropdownOpen = ref(false);
const loading = ref(false);
const inputRef = ref<HTMLInputElement | null>(null);
const options = ref<EmployeeDirectoryListItem[]>([]);

function subjectLabel(item: Pick<EmployeeDirectoryListItem, "feishuSubjectName" | "feishuSubjectCode">): string {
  return (item.feishuSubjectName || item.feishuSubjectCode || "").trim();
}

/** 下拉与输入框展示：姓名（飞书主体）· 部门 */
function formatOptionLabel(item: {
  userId: string;
  name: string;
  departmentName?: string;
  feishuSubjectName?: string;
  feishuSubjectCode?: string;
}): string {
  const name = (item.name || item.userId || "").trim() || item.userId;
  const sub = subjectLabel(item);
  const dept = (item.departmentName || "").trim();
  let out = name;
  if (sub) out += `（${sub}）`;
  if (dept) out += ` · ${dept}`;
  return out;
}

function resolveSelectedName() {
  if (!model.value) return "";
  const matched = options.value.find((item) => item.userId === model.value);
  if (matched) return formatOptionLabel(matched);
  return props.selectedName?.trim() || model.value;
}

function syncInputFromModel() {
  keyword.value = model.value ? resolveSelectedName() : "";
}

const visibleOptions = computed(() => {
  const excluded = new Set(props.excludeUserIds.filter(Boolean));
  const query = keyword.value.trim().toLowerCase();
  let rows = options.value.filter((item) => !excluded.has(item.userId));
  if (query) {
    rows = rows.filter((item) => {
      const haystack = [
        item.name,
        item.departmentName,
        item.userId,
        item.feishuSubjectName,
        item.feishuSubjectCode,
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return haystack.includes(query);
    });
  }
  if (model.value && !excluded.has(model.value) && !rows.some((item) => item.userId === model.value)) {
    const name = props.selectedName?.trim() || model.value;
    return [{ userId: model.value, name }, ...rows];
  }
  return rows;
});

async function loadOptions() {
  loading.value = true;
  try {
    const res = await getAllEmployees();
    options.value = res.items.map((item) => ({
      userId: item.userId,
      name: (item.name || item.userId).trim() || item.userId,
      departmentName: item.departmentName,
      feishuSubjectCode: item.feishuSubjectCode,
      feishuSubjectName: item.feishuSubjectName,
    }));
  } catch {
    options.value = [];
  } finally {
    loading.value = false;
  }
}

function openDropdown() {
  dropdownOpen.value = true;
  if (!loading.value && options.value.length === 0) {
    void loadOptions();
  }
  requestAnimationFrame(() => inputRef.value?.select());
}

function closeDropdown() {
  dropdownOpen.value = false;
  syncInputFromModel();
}

function onInput() {
  if (model.value && keyword.value.trim() !== resolveSelectedName().trim()) {
    model.value = "";
  }
  dropdownOpen.value = true;
}

function selectOption(userId: string) {
  if (!userId) {
    model.value = "";
    keyword.value = "";
  } else {
    const picked = visibleOptions.value.find((item) => item.userId === userId);
    model.value = userId;
    keyword.value = picked ? formatOptionLabel(picked) : "";
  }
  dropdownOpen.value = false;
}

function clearSelection() {
  model.value = "";
  keyword.value = "";
  dropdownOpen.value = true;
  inputRef.value?.focus();
}

onMounted(async () => {
  await loadOptions();
  syncInputFromModel();
});

watch(
  () => [model.value, props.selectedName] as const,
  () => {
    if (!dropdownOpen.value) syncInputFromModel();
  },
);

watch(
  () => props.excludeUserIds.join(","),
  () => {
    if (model.value && props.excludeUserIds.includes(model.value)) {
      model.value = "";
      keyword.value = "";
    }
  },
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
          @click="selectOption('')"
        >
          {{ placeholder }}
        </button>
      </li>
      <li v-if="loading" class="px-3 py-2 text-sm text-muted-foreground">加载中…</li>
      <li v-else-if="visibleOptions.length === 0" class="px-3 py-2 text-sm text-muted-foreground">
        没有匹配的员工，换个关键词试试
      </li>
      <li v-for="item in visibleOptions" :key="item.userId">
        <button
          type="button"
          class="w-full px-3 py-2 text-left text-sm hover:bg-accent"
          :class="model === item.userId ? 'bg-accent font-medium text-accent-foreground' : 'text-foreground'"
          @click="selectOption(item.userId)"
        >
          {{ formatOptionLabel(item) }}
        </button>
      </li>
    </ul>
  </div>
</template>
