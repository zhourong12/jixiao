<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import type { NotificationListItem, SendType } from "@/types/api.interface";
import { getNotifications, sendNotification } from "@/api/notifications";

const activeTab = ref<"send" | "history">("send");
const loading = ref(false);
const notifications = ref<NotificationListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 10;

const sendType = ref<SendType>("all");
const title = ref("");
const content = ref("");
const targetIdsText = ref("");
const sending = ref(false);
const message = ref<string | null>(null);

async function fetchHistory() {
  loading.value = true;
  try {
    const result = await getNotifications({ page: page.value, pageSize });
    notifications.value = result.items;
    total.value = result.total;
  } catch (e) {
    notifications.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "????";
  } finally {
    loading.value = false;
  }
}

async function handleSend() {
  if (!title.value.trim() || !content.value.trim()) {
    message.value = "????????";
    return;
  }
  let targetIds: string[] = [];
  if (sendType.value === "specified" || sendType.value === "department") {
    targetIds = targetIdsText.value
      .split(/[\s,?]+/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (targetIds.length === 0) {
      message.value = sendType.value === "department" ? "????? ID" : "????? ID";
      return;
    }
  } else {
    targetIds = ["all"];
  }
  sending.value = true;
  message.value = null;
  try {
    await sendNotification({
      title: title.value.trim(),
      content: content.value.trim(),
      sendType: sendType.value,
      targetIds,
    });
    title.value = "";
    content.value = "";
    targetIdsText.value = "";
    message.value = "?????";
  } catch (e) {
    message.value = e instanceof Error ? e.message : "????";
  } finally {
    sending.value = false;
  }
}

watch(activeTab, (tab) => {
  if (tab === "history") void fetchHistory();
});

watch(page, () => {
  if (activeTab.value === "history") void fetchHistory();
});

onMounted(() => {
  if (activeTab.value === "history") void fetchHistory();
});
</script>

<template>
  <div class="space-y-6">
    <h1 class="text-2xl font-bold">????</h1>
    <p v-if="message" class="text-sm" :class="message === '?????' ? 'text-success' : 'text-destructive'">
      {{ message }}
    </p>
    <div class="flex gap-2 border-b border-border">
      <button
        type="button"
        class="border-b-2 px-3 py-2 text-sm"
        :class="activeTab === 'send' ? 'border-primary text-primary' : 'border-transparent'"
        @click="activeTab = 'send'"
      >
        ????
      </button>
      <button
        type="button"
        class="border-b-2 px-3 py-2 text-sm"
        :class="activeTab === 'history' ? 'border-primary text-primary' : 'border-transparent'"
        @click="activeTab = 'history'"
      >
        ????
      </button>
    </div>
    <div v-if="activeTab === 'send'" class="max-w-xl space-y-4 rounded-md border border-border bg-card p-6 shadow-sm">
      <div>
        <label class="mb-1 block text-sm font-medium">????</label>
        <select v-model="sendType" class="w-full rounded-md border px-3 py-2 text-sm">
          <option value="all">??</option>
          <option value="department">???</option>
          <option value="specified">????</option>
        </select>
      </div>
      <div v-if="sendType !== 'all'">
        <label class="mb-1 block text-sm font-medium">{{ sendType === "department" ? "?? ID" : "?? ID" }}</label>
        <textarea
          v-model="targetIdsText"
          rows="2"
          class="w-full rounded-md border px-3 py-2 text-sm"
          placeholder="?? ID ?????"
        />
      </div>
      <div>
        <label class="mb-1 block text-sm font-medium">??</label>
        <input v-model="title" class="w-full rounded-md border px-3 py-2 text-sm" />
      </div>
      <div>
        <label class="mb-1 block text-sm font-medium">??</label>
        <textarea v-model="content" rows="4" class="w-full rounded-md border px-3 py-2 text-sm" />
      </div>
      <button
        type="button"
        class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
        :disabled="sending"
        @click="handleSend"
      >
        {{ sending ? "????" : "??" }}
      </button>
    </div>
    <div v-else>
      <div v-if="loading" class="py-12 text-center text-muted-foreground">????</div>
      <div v-else class="overflow-x-auto rounded-md border border-border">
        <table class="w-full min-w-[640px] text-left text-sm">
          <thead class="border-b bg-muted/40">
            <tr>
              <th class="px-3 py-2">??</th>
              <th class="px-3 py-2">??</th>
              <th class="px-3 py-2">????</th>
              <th class="px-3 py-2">???</th>
              <th class="px-3 py-2">??/??</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="n in notifications" :key="n.id" class="border-b hover:bg-accent/40">
              <td class="px-3 py-2">{{ n.title }}</td>
              <td class="px-3 py-2">{{ n.sendType }}</td>
              <td class="px-3 py-2">{{ n.sendTime }}</td>
              <td class="px-3 py-2">{{ n.senderName }}</td>
              <td class="px-3 py-2">{{ n.readCount }}/{{ n.totalCount }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="total > pageSize" class="mt-3 flex gap-2">
        <button type="button" class="rounded-md border px-3 py-1 text-sm" :disabled="page <= 1" @click="page--">
          ???
        </button>
        <button
          type="button"
          class="rounded-md border px-3 py-1 text-sm"
          :disabled="page * pageSize >= total"
          @click="page++"
        >
          ???
        </button>
      </div>
    </div>
  </div>
</template>
