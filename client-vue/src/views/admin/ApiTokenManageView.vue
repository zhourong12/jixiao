<script setup lang="ts">
import { onMounted, ref } from "vue";
import type { ApiTokenItem } from "@/types/api.interface";
import { createApiToken, deleteApiToken, listApiTokens } from "@/api/apiTokens";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const items = ref<ApiTokenItem[]>([]);
const loading = ref(true);
const saving = ref(false);
const name = ref("");
const expiresAt = ref("");
const createdToken = ref("");
const message = ref<string | null>(null);
const messageType = ref<"success" | "error">("success");

async function load() {
  if (!session.allow("admin_api_tokens")) {
    items.value = [];
    loading.value = false;
    return;
  }
  loading.value = true;
  message.value = null;
  try {
    const data = await listApiTokens();
    items.value = Array.isArray(data.items) ? data.items : [];
  } catch (e) {
    messageType.value = "error";
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function createToken() {
  const trimmedName = name.value.trim();
  if (!trimmedName) {
    messageType.value = "error";
    message.value = "请填写 Token 名称";
    return;
  }
  saving.value = true;
  message.value = null;
  createdToken.value = "";
  try {
    const res = await createApiToken({
      name: trimmedName,
      expiresAt: expiresAt.value || null,
    });
    createdToken.value = res.token;
    name.value = "";
    expiresAt.value = "";
    await load();
  } catch (e) {
    messageType.value = "error";
    message.value = e instanceof Error ? e.message : "创建失败";
  } finally {
    saving.value = false;
  }
}

async function remove(row: ApiTokenItem) {
  if (!window.confirm(`确定删除 Token「${row.name}」吗？`)) return;
  try {
    await deleteApiToken(row.id);
    await load();
  } catch (e) {
    messageType.value = "error";
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

async function copyToken() {
  if (!createdToken.value) return;
  await navigator.clipboard.writeText(createdToken.value);
  messageType.value = "success";
  message.value = "Token 已复制";
}

function formatDate(value?: string | number | null): string {
  if (value == null || value === "") return "长期有效";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return d.toLocaleString("zh-CN", { hour12: false });
}

onMounted(() => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p class="mt-1 max-w-2xl text-sm text-muted-foreground">
          为 Agent / Skill 创建 API Token。Token 继承创建者当前角色权限，明文只在创建后显示一次。
        </p>
      </div>
    </div>

    <p v-if="message" class="text-sm" :class="messageType === 'error' ? 'text-destructive' : 'text-success'">
      {{ message }}
    </p>

    <div v-if="!session.allow('admin_api_tokens')" class="ui-card py-16 text-center text-sm text-muted-foreground">
      当前账号无此菜单权限
    </div>

    <template v-else>
      <section class="ui-card space-y-4 p-6">
        <div>
          <h2 class="text-base font-semibold text-foreground">创建 Token</h2>
          <p class="mt-1 text-xs text-muted-foreground">建议按用途命名，例如 performance-summary-review。</p>
        </div>
        <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_240px_auto]">
          <label class="space-y-1">
            <span class="ui-label">名称</span>
            <input v-model="name" class="ui-input" placeholder="请输入 Token 名称" />
          </label>
          <label class="space-y-1">
            <span class="ui-label">过期时间（可选）</span>
            <input v-model="expiresAt" type="datetime-local" class="ui-input" />
          </label>
          <div class="flex items-end">
            <button type="button" class="ui-btn-primary min-h-11 w-full md:w-auto" :disabled="saving" @click="createToken">
              {{ saving ? "创建中..." : "创建" }}
            </button>
          </div>
        </div>
        <div v-if="createdToken" class="rounded-md border border-border bg-accent p-4">
          <p class="text-sm font-medium text-foreground">请立即复制保存，离开页面后无法再次查看明文。</p>
          <div class="mt-3 flex flex-col gap-3 md:flex-row">
            <input :value="createdToken" readonly class="ui-input font-mono text-xs" />
            <button type="button" class="ui-btn-primary shrink-0" @click="copyToken">复制 Token</button>
          </div>
        </div>
      </section>

      <section class="ui-list-panel">
        <div v-if="loading" class="ui-loading">加载中...</div>
        <template v-else>
          <div v-if="items.length === 0" class="ui-empty">暂无 Token</div>
          <div v-else class="ui-table-wrap">
            <table class="ui-table min-w-[720px]">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>创建时间</th>
                  <th>过期时间</th>
                  <th>最后使用</th>
                  <th class="text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in items" :key="row.id">
                  <td class="font-medium">{{ row.name }}</td>
                  <td>{{ formatDate(row.createdAt) }}</td>
                  <td>{{ formatDate(row.expiresAt) }}</td>
                  <td>{{ row.lastUsedAt ? formatDate(row.lastUsedAt) : "从未使用" }}</td>
                  <td class="text-right">
                    <button type="button" class="ui-btn-ghost ui-btn-sm text-destructive" @click="remove(row)">
                      删除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </section>
    </template>
  </div>
</template>
