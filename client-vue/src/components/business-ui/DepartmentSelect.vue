<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { getDepartmentOptions } from "@/api/employees";
import SearchableSelect from "@/components/ui/SearchableSelect.vue";

const model = defineModel<string>({ default: "" });

defineEmits<{
  change: [];
}>();

const props = withDefaults(
  defineProps<{
    placeholder?: string;
    disabled?: boolean;
    subjectCode?: string;
  }>(),
  {
    placeholder: "全部部门",
    disabled: false,
    subjectCode: "",
  },
);

const loading = ref(false);
const options = ref<Array<{ id: string; name: string }>>([]);

const selectOptions = computed(() =>
  options.value.map((item) => ({
    value: item.id,
    label: item.name,
  })),
);

async function load() {
  loading.value = true;
  try {
    const res = await getDepartmentOptions(
      props.subjectCode?.trim() ? { subjectCode: props.subjectCode } : undefined,
    );
    options.value = (res.items ?? []).map((item) => ({
      id: item.id,
      name: item.name?.trim() || item.id,
    }));
  } catch {
    options.value = [];
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
    void load();
  },
  { flush: "post" },
);
</script>

<template>
  <div class="min-w-[10rem]">
    <SearchableSelect
      v-model="model"
      :options="selectOptions"
      :placeholder="placeholder"
      :disabled="disabled"
      :loading="loading"
      empty-text="没有匹配的部门，换个关键词试试"
      @change="$emit('change')"
    />
  </div>
</template>
