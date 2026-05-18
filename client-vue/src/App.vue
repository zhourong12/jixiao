<script setup lang="ts">
import { storeToRefs } from "pinia";
import { RouterView } from "vue-router";
import { useSessionStore } from "@/stores/session";
import AppToast from "@/components/ui/AppToast.vue";

const { loaded } = storeToRefs(useSessionStore());
</script>

<template>
  <div
    v-if="!loaded"
    class="flex min-h-screen items-center justify-center bg-background text-sm text-muted-foreground"
  >
    加载中...
  </div>
  <RouterView v-show="loaded" />
  <AppToast />
</template>
