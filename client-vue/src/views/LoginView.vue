<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { apiJson } from "@/api/http";
import { usePasswordLoginEnabled } from "@/composables/usePasswordLoginEnabled";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);
const { passwordLoginEnabled, loaded, feishuSubjects } = usePasswordLoginEnabled({ loadFeishuSubjects: true });

const showPasswordForm = computed(() => loaded.value && passwordLoginEnabled.value);
const feishuOnly = computed(() => loaded.value && !passwordLoginEnabled.value);

onMounted(() => {
  const q = route.query.login_error;
  const msg = typeof q === "string" ? q : Array.isArray(q) && typeof q[0] === "string" ? q[0] : "";
  if (msg) {
    try {
      error.value = decodeURIComponent(msg.replace(/\+/g, " "));
    } catch {
      error.value = msg;
    }
  }
});

async function onSubmit() {
  error.value = "";
  loading.value = true;
  try {
    await apiJson<{ success?: boolean }>("/auth/password/login", {
      method: "POST",
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    });
    await session.refreshSession();
    await session.refreshMenuPermissions(true);
    const next = typeof route.query.next === "string" ? route.query.next : "/";
    await router.replace(next || "/");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "登录失败";
  } finally {
    loading.value = false;
  }
}

function goFeishuWithSubject(subjectCode: string) {
  error.value = "";
  const code = subjectCode.trim();
  if (!code) return;
  const next = typeof route.query.next === "string" ? route.query.next : "/";
  window.location.href = `/auth/feishu/login?subjectCode=${encodeURIComponent(code)}&next=${encodeURIComponent(next)}`;
}
</script>

<template>
  <div
    class="relative flex min-h-screen items-center justify-center overflow-hidden p-4 md:p-6"
    :class="feishuOnly ? 'bg-[hsl(215_25%_97%)]' : 'bg-background'"
  >
    <div
      v-if="feishuOnly"
      class="pointer-events-none absolute inset-0 opacity-40"
      aria-hidden="true"
      style="
        background-image: linear-gradient(hsl(214 20% 90% / 0.6) 1px, transparent 1px),
          linear-gradient(90deg, hsl(214 20% 90% / 0.6) 1px, transparent 1px);
        background-size: 48px 48px;
      "
    />

    <div
      class="relative w-full shadow-sm"
      :class="feishuOnly ? 'max-w-md rounded-lg border border-border/60 bg-card p-6 md:p-10' : 'ui-card max-w-lg p-6 md:p-8'"
    >
      <div
        class="flex flex-col items-center text-center"
        :class="feishuOnly ? '' : 'sm:flex-row sm:items-start sm:text-left sm:gap-4'"
      >
        <img
          src="/app-logo.png"
          alt="科臻赛绩效"
          class="shrink-0 rounded-lg object-contain"
          :class="feishuOnly ? 'h-16 w-16' : 'h-14 w-14'"
        />
        <div class="min-w-0 flex-1" :class="feishuOnly ? 'mt-6' : ''">
          <h1 class="text-2xl font-bold leading-tight text-foreground">科臻赛绩效</h1>
          <p v-if="showPasswordForm" class="mt-1 text-sm text-muted-foreground">
            使用员工编号或姓名登录；演示环境默认密码为 123456。
          </p>
        </div>
      </div>

      <template v-if="showPasswordForm">
        <form class="mt-8 space-y-4" @submit.prevent="onSubmit">
          <div>
            <label class="ui-label">员工编号或姓名</label>
            <input
              v-model="username"
              type="text"
              autocomplete="username"
              placeholder="如 zhou_rong 或 周荣"
              class="ui-input"
              required
            />
          </div>
          <div>
            <label class="ui-label">密码</label>
            <input v-model="password" type="password" autocomplete="current-password" class="ui-input" required />
          </div>
          <p v-if="error" class="ui-alert-danger">{{ error }}</p>
          <button type="submit" class="ui-btn-primary w-full" :disabled="loading">
            {{ loading ? "登录中…" : "登录" }}
          </button>
        </form>

        <div class="relative my-8">
          <div class="absolute inset-0 flex items-center" aria-hidden="true">
            <div class="w-full border-t border-border" />
          </div>
          <div class="relative flex justify-center">
            <span class="bg-card px-3 text-xs font-medium text-muted-foreground">其他登录方式</span>
          </div>
        </div>

        <div v-if="feishuSubjects.length" class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <button
            v-for="s in feishuSubjects"
            :key="s.code"
            type="button"
            class="flex min-h-[44px] items-center gap-3 rounded-md border border-border bg-card p-4 text-left transition-colors duration-150 hover:border-primary/40 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            @click="goFeishuWithSubject(s.code)"
          >
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-md text-sm font-semibold text-white"
              style="background: linear-gradient(135deg, #3370ff 0%, #1e4fc4 100%)"
            >
              飞
            </div>
            <div class="min-w-0 flex-1">
              <div class="truncate text-sm font-semibold text-foreground">{{ s.name }}</div>
            </div>
          </button>
        </div>
        <p v-else class="text-center text-xs text-muted-foreground">
          未配置可登录的飞书主体时无法使用飞书登录，请联系管理员在库中维护 feishu_subject / feishu_app。
        </p>
      </template>

      <template v-else>
        <p v-if="error" class="ui-alert-danger mt-8">{{ error }}</p>
        <p v-if="!loaded" class="mt-10 text-center text-sm text-muted-foreground">加载中…</p>
        <div v-else-if="feishuSubjects.length" class="mt-10 space-y-3">
          <button
            v-for="s in feishuSubjects"
            :key="s.code"
            type="button"
            class="flex min-h-[48px] w-full items-center justify-center rounded-md bg-primary px-4 py-3.5 text-sm font-medium text-primary-foreground shadow-sm transition-opacity duration-150 hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
            @click="goFeishuWithSubject(s.code)"
          >
            飞书账号登录（{{ s.name }}）
          </button>
        </div>
        <p v-else-if="loaded" class="mt-10 text-center text-sm text-muted-foreground">
          未配置可登录的飞书主体，请联系管理员在库中维护 feishu_subject / feishu_app。
        </p>
      </template>
    </div>
  </div>
</template>
