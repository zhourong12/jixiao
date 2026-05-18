<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { DepartmentTreeSubject } from "@/types/api.interface";
import { getDepartmentTree } from "@/api/employees";
import { deptFilterValue, subjectFilterValue } from "@/utils/departmentFilter";

const model = defineModel<string>({ default: "" });

const emit = defineEmits<{
  change: [];
}>();

const props = withDefaults(
  defineProps<{
    placeholder?: string;
    disabled?: boolean;
    /** 仅展示该飞书主体下的部门（员工表单用） */
    subjectCode?: string;
    /** 仅允许选择具体部门，不可选主体 */
    deptOnly?: boolean;
    /** 是否显示「全部」清空项 */
    showClearOption?: boolean;
  }>(),
  {
    placeholder: "全部部门",
    disabled: false,
    subjectCode: "",
    deptOnly: false,
    showClearOption: true,
  },
);

const loading = ref(false);
const tree = ref<DepartmentTreeSubject[]>([]);
const keyword = ref("");
const dropdownOpen = ref(false);
const inputRef = ref<HTMLInputElement | null>(null);
const expanded = ref<Set<string>>(new Set());

function labelFor(value: string) {
  if (!value) return "";
  if (value.startsWith("subject:")) {
    const code = value.slice("subject:".length);
    const sub = tree.value.find((s) => s.subjectCode === code);
    return sub?.subjectName?.trim() || code;
  }
  if (value.startsWith("dept:")) {
    const rest = value.slice("dept:".length);
    const sep = rest.indexOf(":");
    const subjectCode = sep > 0 ? rest.slice(0, sep) : "";
    const id = sep > 0 ? rest.slice(sep + 1) : rest;
    for (const sub of tree.value) {
      if (subjectCode && sub.subjectCode !== subjectCode) continue;
      const dept = sub.departments.find((d) => d.id === id);
      if (dept) {
        const deptName = dept.name?.trim() || dept.id;
        const subName = sub.subjectName?.trim() || sub.subjectCode;
        return `${subName} / ${deptName}`;
      }
    }
    return id;
  }
  return value;
}

function syncFromModel() {
  keyword.value = model.value ? labelFor(model.value) : "";
}

function openDropdown() {
  dropdownOpen.value = true;
  if (expanded.value.size === 0) {
    expanded.value = new Set(tree.value.map((s) => s.subjectCode));
  }
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
  if (keyword.value.trim()) {
    expanded.value = new Set(tree.value.map((s) => s.subjectCode));
  }
}

function clearSelection() {
  model.value = "";
  keyword.value = "";
  dropdownOpen.value = true;
  inputRef.value?.focus();
}

function toggleExpand(code: string) {
  const next = new Set(expanded.value);
  if (next.has(code)) next.delete(code);
  else next.add(code);
  expanded.value = next;
}

const scopedTree = computed(() => {
  const sc = props.subjectCode?.trim();
  if (!sc) return tree.value;
  return tree.value.filter((s) => s.subjectCode === sc);
});

const filteredTree = computed(() => {
  const base = scopedTree.value;
  const q = keyword.value.trim().toLowerCase();
  if (!q || (model.value && q === labelFor(model.value).trim().toLowerCase())) {
    return base;
  }
  return base
    .map((sub) => {
      const subName = (sub.subjectName || sub.subjectCode).toLowerCase();
      const subMatch = subName.includes(q) || sub.subjectCode.toLowerCase().includes(q);
      const depts = sub.departments.filter((d) => {
        const name = (d.name || d.id).toLowerCase();
        return name.includes(q) || d.id.toLowerCase().includes(q);
      });
      if (subMatch) return { ...sub, departments: sub.departments };
      if (depts.length) return { ...sub, departments: depts };
      return null;
    })
    .filter((x): x is DepartmentTreeSubject => x != null);
});

async function load() {
  loading.value = true;
  try {
    const res = await getDepartmentTree();
    tree.value = res.items ?? [];
    expanded.value = new Set(tree.value.map((s) => s.subjectCode));
  } catch {
    tree.value = [];
    expanded.value = new Set();
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
});

watch(
  () => props.subjectCode,
  () => {
    if (props.subjectCode?.trim()) {
      expanded.value = new Set([props.subjectCode.trim()]);
    }
    if (props.deptOnly && model.value.startsWith("subject:")) {
      model.value = "";
      syncFromModel();
    }
  },
);

watch(
  () => [model.value, tree.value] as const,
  () => {
    if (!dropdownOpen.value) syncFromModel();
  },
  { deep: true },
);
</script>

<template>
  <div class="relative min-w-[10rem]">
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
      class="absolute z-50 mt-1 max-h-64 w-full min-w-[14rem] overflow-auto rounded-md border border-border bg-card py-1 shadow-md"
      @mousedown.prevent
    >
      <li v-if="showClearOption && !deptOnly">
        <button
          type="button"
          class="w-full px-3 py-2 text-left text-sm text-muted-foreground hover:bg-accent"
          :class="!model ? 'bg-accent font-medium text-accent-foreground' : ''"
          @click="pick('')"
        >
          {{ placeholder }}
        </button>
      </li>
      <li v-if="loading" class="px-3 py-2 text-sm text-muted-foreground">加载中…</li>
      <li v-else-if="filteredTree.length === 0" class="px-3 py-2 text-sm text-muted-foreground">
        没有匹配的部门，换个关键词试试
      </li>
      <template v-for="sub in filteredTree" :key="sub.subjectCode">
        <li class="flex items-stretch">
          <button
            type="button"
            class="shrink-0 px-2 py-2 text-muted-foreground hover:bg-accent hover:text-foreground"
            :aria-expanded="expanded.has(sub.subjectCode)"
            :aria-label="expanded.has(sub.subjectCode) ? '收起' : '展开'"
            @click="toggleExpand(sub.subjectCode)"
          >
            <span class="inline-block w-3 text-center text-xs">{{ expanded.has(sub.subjectCode) ? "▼" : "▶" }}</span>
          </button>
          <button
            type="button"
            class="min-w-0 flex-1 py-2 pr-3 text-left text-sm font-medium"
            :class="
              deptOnly
                ? 'cursor-default text-foreground'
                : model === subjectFilterValue(sub.subjectCode)
                  ? 'bg-accent text-accent-foreground hover:bg-accent'
                  : 'text-foreground hover:bg-accent'
            "
            @click="deptOnly ? toggleExpand(sub.subjectCode) : pick(subjectFilterValue(sub.subjectCode))"
          >
            {{ sub.subjectName?.trim() || sub.subjectCode }}
            <span v-if="!deptOnly" class="ml-1 text-xs font-normal text-muted-foreground">主体</span>
          </button>
        </li>
        <li
          v-for="dept in expanded.has(sub.subjectCode) ? sub.departments : []"
          :key="`${sub.subjectCode}-${dept.id}`"
        >
          <button
            type="button"
            class="w-full py-2 pl-9 pr-3 text-left text-sm hover:bg-accent"
            :class="
              model === deptFilterValue(sub.subjectCode, dept.id)
                ? 'bg-accent font-medium text-accent-foreground'
                : 'text-foreground'
            "
            @click="pick(deptFilterValue(sub.subjectCode, dept.id))"
          >
            <span class="text-muted-foreground">{{ sub.subjectName?.trim() || sub.subjectCode }} /</span>
            {{ dept.name?.trim() || dept.id }}
          </button>
        </li>
      </template>
    </ul>
  </div>
</template>
