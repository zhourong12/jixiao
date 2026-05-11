<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useSessionStore } from "@/stores/session";

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const username = ref("");
const password = ref("");
const error = ref<string | null>(null);
const loading = ref(false);

function goFeishu() {
  window.location.href = "/auth/feishu/login";
}

async function onSubmit() {
  error.value = null;
  loading.value = true;
  try {
    const res = await fetch("/auth/password/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    });
    const data = (await res.json()) as { success?: boolean; message?: string };
    if (!res.ok || !data.success) {
      error.value = data.message || "????";
      return;
    }
    await session.refreshSession();
    await session.refreshMenuPermissions(true);
    const redirect = typeof route.query.redirect === "string" ? route.query.redirect : "/";
    await router.replace(redirect || "/");
  } catch {
    error.value = "????";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-background p-4">
    <div class="w-full max-w-md rounded-md border border-border bg-card p-6 shadow-sm">
      <h1 class="text-xl font-semibold text-foreground">????</h1>
      <p class="mt-1 text-sm text-muted-foreground">??????????????????????</p>
      <form class="mt-6 space-y-4" @submit.prevent="onSubmit">
        <p v-if="error" class="text-sm text-destructive" role="alert">{{ error }}</p>
        <div>
          <label class="mb-1 block text-sm font-medium text-foreground" for="u">???</label>
          <input
            id="u"
            v-model="username"
            autocomplete="username"
            class="w-full rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
            placeholder="???????"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-foreground" for="p">??</label>
          <input
            id="p"
            v-model="password"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-md border border-border bg-background px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-primary"
            placeholder="?? 123456"
          />
        </div>
        <button
          type="submit"
          class="w-full rounded-md bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:opacity-50"
          :disabled="loading"
        >
          {{ loading ? "???..." : "??" }}
        </button>
        <button
          type="button"
          class="w-full rounded-md border border-border px-4 py-2.5 text-sm font-medium hover:bg-accent"
          @click="goFeishu"
        >
          ????
        </button>
      </form>
    </div>
  </div>
</template>
