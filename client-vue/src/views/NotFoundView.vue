<script setup lang="ts">
import { RouterLink } from "vue-router";
</script>

<template>
  <div class="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6">
    <p class="text-lg font-medium text-foreground">页面不存在</p>
    <RouterLink to="/todo" class="text-sm text-primary hover:underline">返回待办</RouterLink>
  </div>
</template>
