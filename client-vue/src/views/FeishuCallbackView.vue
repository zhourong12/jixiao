<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

function firstQuery(v: unknown): string {
  if (typeof v === "string") return v;
  if (Array.isArray(v) && typeof v[0] === "string") return v[0];
  return "";
}

onMounted(() => {
  const code = firstQuery(route.query.code);
  const state = firstQuery(route.query.state);
  if (!code) {
    const err = firstQuery(route.query.login_error) || "缺少飞书授权码";
    void router.replace({ path: "/login", query: { login_error: err } });
    return;
  }
  const qs = new URLSearchParams();
  qs.set("code", code);
  if (state) qs.set("state", state);
  window.location.href = `/auth/feishu/exchange?${qs.toString()}`;
});
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-background p-6 text-sm text-muted-foreground">
    正在完成飞书登录…
  </div>
</template>
