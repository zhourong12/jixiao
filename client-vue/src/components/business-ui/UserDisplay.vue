<script setup lang="ts">
import { computed } from "vue";
import { getUserDisplayName, isValidUserId, normalizeUser, shortPersonDisplayName, type UserInput } from "@/utils/user";

const props = withDefaults(
  defineProps<{
    userId?: string;
    user_id?: string;
    value?: string | UserInput | Array<string | UserInput>;
    size?: "small" | "medium" | "large";
    showLabel?: boolean;
  }>(),
  {
    size: "medium",
    showLabel: true,
  },
);

const users = computed(() => {
  let list: UserInput[] = [];
  if (props.value) {
    const raw = Array.isArray(props.value) ? props.value : [props.value];
    list = raw.map((item) => (typeof item === "string" ? { user_id: item } : item));
  } else if (props.userId) {
    list = [{ user_id: props.userId }];
  } else if (props.user_id) {
    list = [{ user_id: props.user_id }];
  }
  return list.map(normalizeUser).filter((user) => isValidUserId(user.user_id));
});

const sizeClass = computed(() => {
  if (props.size === "small") return "h-8 gap-2 text-sm leading-5";
  if (props.size === "large") return "h-10 gap-2.5 text-base";
  return "h-9 gap-2 text-sm";
});

const avatarClass = computed(() => {
  if (props.size === "small") return "size-7 text-xs";
  if (props.size === "large") return "size-10 text-sm";
  return "size-8 text-xs";
});

function initial(name: string): string {
  const t = name.trim();
  return t ? t.slice(0, 1) : "?";
}

function labelFor(user: ReturnType<typeof normalizeUser>): string {
  return shortPersonDisplayName(getUserDisplayName(user));
}
</script>

<template>
  <div v-if="users.length" class="flex flex-wrap gap-1">
    <div
      v-for="user in users"
      :key="user.user_id"
      class="inline-flex items-center rounded-md px-1 py-0.5 hover:bg-accent/80"
      :class="sizeClass"
      :title="getUserDisplayName(user) || user.departmentName || user.user_id"
    >
      <span
        class="inline-flex shrink-0 items-center justify-center rounded-full bg-accent font-semibold text-accent-foreground"
        :class="avatarClass"
      >
        <img v-if="user.avatar" :src="user.avatar" alt="" class="size-full rounded-full object-cover" />
        <span v-else>{{ initial(labelFor(user)) }}</span>
      </span>
      <span v-if="showLabel" class="truncate font-medium text-foreground">{{ labelFor(user) }}</span>
    </div>
  </div>
</template>
