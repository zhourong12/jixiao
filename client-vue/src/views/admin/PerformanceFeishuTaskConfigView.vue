<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { PerformanceFeishuTaskNodeConfigItem } from "@/types/api.interface";
import {
  getPerformanceFeishuTaskConfig,
  patchPerformanceFeishuTaskConfig,
} from "@/api/performanceFeishuTaskConfig";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const allowed = computed(() => session.loaded && session.allow("admin_performance_feishu_task"));

const items = ref<PerformanceFeishuTaskNodeConfigItem[]>([]);
const enabled = ref(true);
const savedEnabled = ref(true);
const editDue = ref<Record<string, number>>({});
const loading = ref(true);
const saving = ref(false);
const message = ref<string | null>(null);

const hasChanges = computed(
  () =>
    enabled.value !== savedEnabled.value ||
    items.value.some((row) => (editDue.value[row.nodeKey] ?? row.dueDays) !== row.dueDays),
);

async function load() {
  if (!allowed.value) {
    loading.value = false;
    items.value = [];
    return;
  }
  loading.value = true;
  message.value = null;
  try {
    const data = await getPerformanceFeishuTaskConfig();
    items.value = Array.isArray(data.items) ? data.items : [];
    enabled.value = data.enabled ?? true;
    savedEnabled.value = enabled.value;
    const m: Record<string, number> = {};
    for (const row of items.value) {
      m[row.nodeKey] = row.dueDays;
    }
    editDue.value = m;
  } catch (e) {
    items.value = [];
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  message.value = null;
  try {
    await patchPerformanceFeishuTaskConfig({
      enabled: enabled.value,
      items: items.value.map((row) => ({
        nodeKey: row.nodeKey,
        dueDays: editDue.value[row.nodeKey] ?? row.dueDays,
      })),
    });
    message.value = "ok";
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "保存失败";
  } finally {
    saving.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <h1 class="text-2xl font-bold text-foreground">飞书绩效待办</h1>
        <p class="mt-1 text-sm text-muted-foreground">
          开启后，在发送飞书通知时会同步创建飞书待办；关闭后不再新建待办（已有待办不受影响）。下方按流程节点配置截止天数（从发送通知时起算，截止日为第 N 天 23:59，上海时区）。
        </p>
      </div>
      <button
        v-if="allowed"
        type="button"
        class="ui-btn-primary"
        :disabled="saving || !hasChanges || items.length === 0"
        @click="save"
      >
        {{ saving ? "保存中..." : "保存" }}
      </button>
    </div>
    <p v-if="message" class="text-sm" :class="message === 'ok' ? 'text-success' : 'text-destructive'">
      {{ message === "ok" ? "已保存" : message }}
    </p>
    <div v-if="!allowed" class="ui-card py-16 text-center text-sm text-muted-foreground">当前账号无此菜单权限</div>
    <div v-else-if="loading" class="py-16 text-center text-muted-foreground">加载中...</div>
    <section v-else class="ui-card space-y-4 p-6">
      <label class="flex cursor-pointer items-center gap-3 rounded-md border border-border px-4 py-3">
        <input v-model="enabled" type="checkbox" class="h-4 w-4 shrink-0 rounded border" />
        <div>
          <p class="text-sm font-medium text-foreground">启用飞书绩效待办</p>
          <p class="mt-0.5 text-xs text-muted-foreground">关闭后不再为新的飞书通知创建待办任务</p>
        </div>
      </label>
      <div class="ui-table-wrap overflow-x-auto" :class="{ 'pointer-events-none opacity-50': !enabled }">
        <table class="ui-table w-full min-w-[480px]">
          <thead>
            <tr>
              <th class="py-3.5 text-left text-sm font-medium">流程节点</th>
              <th class="py-3.5 text-left text-sm font-medium">截止天数（天）</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.nodeKey" class="border-t border-border py-4">
              <td class="py-4 text-sm">{{ row.name }}</td>
              <td class="py-4">
                <input
                  v-model.number="editDue[row.nodeKey]"
                  type="number"
                  min="0"
                  max="3650"
                  class="ui-input w-28 max-w-full"
                  :disabled="!enabled"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
