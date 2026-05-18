<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getFeishuLoginSubjects } from "@/api/employees";

const route = useRoute();
const router = useRouter();
const message = ref("正在跳转飞书登录…");
const error = ref("");

function firstQuery(key: string): string {
  const v = route.query[key];
  if (typeof v === "string") return v;
  if (Array.isArray(v) && typeof v[0] === "string") return v[0];
  return "";
}

function resolveSubjectCode(): string {
  const param = route.params.subjectCode;
  if (typeof param === "string" && param.trim()) return param.trim();
  return (firstQuery("subjectCode") || firstQuery("subject") || firstQuery("code")).trim();
}

function resolveNext(): string {
  const n = firstQuery("next").trim();
  if (n && n.startsWith("/") && !n.startsWith("//")) return n;
  return "/";
}

function startFeishuLogin(subjectCode: string) {
  const qs = new URLSearchParams();
  qs.set("subjectCode", subjectCode);
  const next = resolveNext();
  if (next !== "/") qs.set("next", next);
  window.location.href = `/auth/feishu/login?${qs.toString()}`;
}

onMounted(async () => {
  let code = resolveSubjectCode();
  if (!code) {
    try {
      const res = await getFeishuLoginSubjects();
      const items = res.items || [];
      if (items.length === 1) {
        code = items[0].code;
      } else if (items.length === 0) {
        error.value = "未配置飞书登录主体，请联系管理员";
        return;
      } else {
        void router.replace({ path: "/login", query: { ...route.query } });
        return;
      }
    } catch {
      void router.replace({ path: "/login", query: { login_error: "无法加载飞书主体配置" } });
      return;
    }
  }
  startFeishuLogin(code);
});
</script>

<template>
  <main class="flex min-h-screen flex-col items-center justify-center gap-3 bg-background p-6 text-sm text-muted-foreground">
    <p v-if="error" class="text-destructive">{{ error }}</p>
    <template v-else>
      <p>{{ message }}</p>
      <p class="text-xs">若长时间无响应，请返回登录页重试</p>
    </template>
  </main>
</template>
