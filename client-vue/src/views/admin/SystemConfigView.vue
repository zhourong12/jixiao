<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { PerformanceFeishuTaskNodeConfigItem } from "@/types/api.interface";
import { getSystemConfig, patchSystemConfig } from "@/api/systemConfig";
import { parsePasswordLoginEnabled, useAuthLoginStore } from "@/stores/authLogin";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const allowed = computed(() => session.loaded && session.allow("admin_performance_feishu_task"));

const authLogin = useAuthLoginStore();
const appBadgeEnabled = ref(true);
const savedAppBadgeEnabled = ref(true);
const feishuTaskEnabled = ref(true);
const savedFeishuTaskEnabled = ref(true);
const passwordLoginEnabled = ref(false);
const savedPasswordLoginEnabled = ref(false);
const items = ref<PerformanceFeishuTaskNodeConfigItem[]>([]);
const editDue = ref<Record<string, number>>({});
const loading = ref(true);
const saving = ref(false);
const message = ref<string | null>(null);

const hasChanges = computed(
  () =>
    appBadgeEnabled.value !== savedAppBadgeEnabled.value ||
    feishuTaskEnabled.value !== savedFeishuTaskEnabled.value ||
    passwordLoginEnabled.value !== savedPasswordLoginEnabled.value ||
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
    const data = await getSystemConfig();
    appBadgeEnabled.value = data.appBadgeEnabled ?? true;
    savedAppBadgeEnabled.value = appBadgeEnabled.value;
    feishuTaskEnabled.value = data.feishuTaskEnabled ?? true;
    savedFeishuTaskEnabled.value = feishuTaskEnabled.value;
    passwordLoginEnabled.value = parsePasswordLoginEnabled(data.passwordLoginEnabled);
    savedPasswordLoginEnabled.value = passwordLoginEnabled.value;
    items.value = Array.isArray(data.feishuTaskItems) ? data.feishuTaskItems : [];
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
    await patchSystemConfig({
      appBadgeEnabled: appBadgeEnabled.value,
      feishuTaskEnabled: feishuTaskEnabled.value,
      passwordLoginEnabled: passwordLoginEnabled.value,
      feishuTaskItems: items.value.map((row) => ({
        nodeKey: row.nodeKey,
        dueDays: editDue.value[row.nodeKey] ?? row.dueDays,
      })),
    });
    message.value = "ok";
    await authLogin.refresh();
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
        <h1 class="text-2xl font-bold text-foreground">系统配置</h1>
        <p class="mt-1 max-w-2xl text-sm text-muted-foreground">
          管理登录方式与飞书相关全局能力。关闭开关后仅停止新行为，不影响历史数据。
        </p>
      </div>
      <button
        v-if="allowed"
        type="button"
        class="ui-btn-primary"
        :disabled="saving || !hasChanges"
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

    <template v-else>
      <section class="ui-card space-y-4 p-6">
        <div>
          <h2 class="text-base font-semibold text-foreground">登录方式</h2>
          <p class="mt-1 text-xs text-muted-foreground">
            开启后，登录页展示用户名/密码表单；关闭后仅保留飞书扫码登录（已登录会话不受影响）。
          </p>
        </div>
        <label class="flex cursor-pointer items-center gap-3 rounded-md border border-border px-4 py-3">
          <input v-model="passwordLoginEnabled" type="checkbox" class="h-4 w-4 shrink-0 rounded border" />
          <div>
            <p class="text-sm font-medium text-foreground">允许账密登录</p>
            <p class="mt-0.5 text-xs text-muted-foreground">关闭后新访客无法使用本地账号密码登录</p>
          </div>
        </label>
      </section>

      <section class="ui-card space-y-4 p-6">
        <div>
          <h2 class="text-base font-semibold text-foreground">飞书应用角标</h2>
          <p class="mt-1 text-xs text-muted-foreground">
            开启后，根据站内待办数同步飞书工作台网页应用角标；关闭后不再调用飞书角标 API（客户端在飞书内打开时也不再初始化角标）。
          </p>
        </div>
        <label class="flex cursor-pointer items-center gap-3 rounded-md border border-border px-4 py-3">
          <input v-model="appBadgeEnabled" type="checkbox" class="h-4 w-4 shrink-0 rounded border" />
          <div>
            <p class="text-sm font-medium text-foreground">启用飞书应用角标</p>
            <p class="mt-0.5 text-xs text-muted-foreground">绩效变更、登录等触发的角标同步将一并停用</p>
          </div>
        </label>
      </section>

      <section class="ui-card space-y-4 p-6">
        <div>
          <h2 class="text-base font-semibold text-foreground">飞书绩效待办</h2>
          <p class="mt-1 text-xs text-muted-foreground">
            开启后，发送飞书绩效通知时会同步创建飞书待办；关闭后不再新建（已有待办不受影响）。下方配置各流程节点截止天数（从发送通知日起算，第 N 天 23:59，上海时区）。
          </p>
        </div>
        <label class="flex cursor-pointer items-center gap-3 rounded-md border border-border px-4 py-3">
          <input v-model="feishuTaskEnabled" type="checkbox" class="h-4 w-4 shrink-0 rounded border" />
          <div>
            <p class="text-sm font-medium text-foreground">启用飞书绩效待办</p>
            <p class="mt-0.5 text-xs text-muted-foreground">关闭后不再为新的飞书通知创建待办任务</p>
          </div>
        </label>
        <div class="ui-table-wrap overflow-x-auto" :class="{ 'pointer-events-none opacity-50': !feishuTaskEnabled }">
          <table class="ui-table w-full min-w-[480px]">
            <thead>
              <tr>
                <th class="py-3.5 text-left text-sm font-medium">流程节点</th>
                <th class="py-3.5 text-left text-sm font-medium">截止天数（天）</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in items" :key="row.nodeKey" class="border-t border-border">
                <td class="py-4 text-sm">{{ row.name }}</td>
                <td class="py-4">
                  <input
                    v-model.number="editDue[row.nodeKey]"
                    type="number"
                    min="0"
                    max="3650"
                    class="ui-input w-28 max-w-full"
                    :disabled="!feishuTaskEnabled"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>
