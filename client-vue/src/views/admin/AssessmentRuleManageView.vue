<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import type { AssessmentRuleListItem, CreateAssessmentRuleRequest } from "@/types/api.interface";
import {
  createAssessmentRule,
  deleteAssessmentRule,
  listAssessmentRules,
  updateAssessmentRule,
} from "@/api/assessmentRules";
import ListPagination from "@/components/ui/ListPagination.vue";
import { formatDateTime } from "@/utils/datetime";

const items = ref<AssessmentRuleListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(true);
const message = ref<string | null>(null);
/** 新建/编辑弹窗内校验或保存失败（列表上方 message 仅用于列表加载错误） */
const dialogError = ref<string | null>(null);
type DialogErrorField = "name" | "weights" | "general";
const dialogErrorField = ref<DialogErrorField | null>(null);

function setDialogError(field: DialogErrorField, msg: string) {
  dialogErrorField.value = field;
  dialogError.value = msg;
}
const dialogOpen = ref(false);
const editingId = ref<string | null>(null);
const formName = ref("");
const formManagerPct = ref(70);
const formDottedPct = ref(30);
const saving = ref(false);

async function load() {
  loading.value = true;
  try {
    const res = await listAssessmentRules(page.value, pageSize.value);
    items.value = res.items;
    total.value = res.total;
  } catch (e) {
    items.value = [];
    total.value = 0;
    message.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  formName.value = "";
  formManagerPct.value = 70;
  formDottedPct.value = 30;
  dialogError.value = null;
  dialogErrorField.value = null;
  message.value = null;
  dialogOpen.value = true;
}

function closeDialog() {
  dialogOpen.value = false;
  editingId.value = null;
  dialogError.value = null;
  dialogErrorField.value = null;
}

function edit(row: AssessmentRuleListItem) {
  editingId.value = row.id;
  formName.value = row.name;
  formManagerPct.value = Math.round(row.managerWeight * 100);
  formDottedPct.value = Math.round(row.dottedManagerWeight * 100);
  dialogError.value = null;
  dialogErrorField.value = null;
  message.value = null;
  dialogOpen.value = true;
}

async function saveRule() {
  const name = formName.value.trim();
  const mp = Number(formManagerPct.value);
  const dp = Number(formDottedPct.value);
  dialogError.value = null;
  dialogErrorField.value = null;
  if (!name) {
    setDialogError("name", "请填写规则名称");
    return;
  }
  if (!Number.isFinite(mp) || !Number.isFinite(dp) || mp < 0 || dp < 0 || (mp <= 0 && dp <= 0) || Math.abs(mp + dp - 100) > 0.5) {
    setDialogError("weights", "直属与虚线占比须为 0～100 的整数、至少一侧大于 0，且合计为 100%（允许一侧为 0、一侧为 100）");
    return;
  }
  const mw = mp / 100;
  const dw = dp / 100;
  saving.value = true;
  try {
    if (editingId.value) {
      await updateAssessmentRule(editingId.value, {
        name,
        managerWeight: mw,
        dottedManagerWeight: dw,
      });
    } else {
      const body: CreateAssessmentRuleRequest = { name, managerWeight: mw, dottedManagerWeight: dw };
      await createAssessmentRule(body);
    }
    closeDialog();
    await load();
  } catch (e) {
    setDialogError("general", e instanceof Error ? e.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function remove(row: AssessmentRuleListItem) {
  if (!window.confirm(`确定删除规则「${row.name}」吗？`)) return;
  try {
    await deleteAssessmentRule(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => void load());
watch([page, pageSize], () => void load());
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div>
        <p class="mt-1 text-sm text-muted-foreground">
          定义直属上级与虚线上级在「绩效指标 + 文化价值观」合成时的权重比例；创建绩效时选用一条启用的规则（可与模板解绑）。
        </p>
      </div>
      <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="openCreate">
        新建规则
      </button>
    </div>
    <p v-if="message && !dialogOpen" class="text-sm text-destructive">{{ message }}</p>
    <section class="ui-list-panel">
      <div v-if="loading" class="ui-loading">加载中...</div>
      <template v-else>
        <div class="ui-mobile-cards p-3">
          <div v-for="row in items" :key="row.id" class="ui-mobile-card">
            <div class="flex items-start justify-between gap-2">
              <span class="font-semibold">{{ row.name }}</span>
              <span class="shrink-0 rounded-full px-2 py-0.5 text-xs" :class="row.status === 'enabled' ? 'bg-[var(--success-bg)] text-[var(--success)]' : 'bg-accent text-muted-foreground'">{{ row.status === "enabled" ? "启用" : "停用" }}</span>
            </div>
            <div class="mt-2 flex flex-wrap gap-3 text-xs text-muted-foreground">
              <span>上级 {{ Math.round(row.managerWeight * 100) }}%</span>
              <span>虚线 {{ Math.round(row.dottedManagerWeight * 100) }}%</span>
            </div>
            <div class="mt-3 flex items-center gap-3">
              <button type="button" class="text-sm text-primary hover:underline" @click="edit(row)">编辑</button>
              <button type="button" class="text-sm text-destructive hover:underline" @click="remove(row)">删除</button>
            </div>
          </div>
        </div>
        <div class="ui-desktop-table">
          <div class="ui-table-wrap">
            <table class="ui-table min-w-[640px]">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>上级权重</th>
                  <th>虚线权重</th>
                  <th>状态</th>
                  <th>更新时间</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in items" :key="row.id">
                  <td>{{ row.name }}</td>
                  <td class="tabular-nums">{{ Math.round(row.managerWeight * 100) }}%</td>
                  <td class="tabular-nums">{{ Math.round(row.dottedManagerWeight * 100) }}%</td>
                  <td>{{ row.status === "enabled" ? "启用" : "停用" }}</td>
                  <td class="text-muted-foreground">{{ formatDateTime(row.updatedAt) }}</td>
                  <td class="text-right">
                    <button type="button" class="mr-2 text-primary hover:underline" @click="edit(row)">编辑</button>
                    <button type="button" class="text-destructive hover:underline" @click="remove(row)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </section>

    <div
      v-if="dialogOpen"
      class="ui-dialog-backdrop"
      @click.self="closeDialog"
    >
      <div class="w-full max-w-md rounded-md border bg-card p-4 shadow-lg md:p-6">
        <h2 class="text-lg font-semibold">{{ editingId ? "编辑考核规则" : "新建考核规则" }}</h2>
        <div class="mt-4 space-y-3">
          <div>
            <label class="mb-1 block text-xs font-medium">规则名称</label>
            <input
              v-model="formName"
              class="w-full rounded-md border px-3 py-2 text-sm"
              :class="dialogErrorField === 'name' ? 'border-destructive' : ''"
              placeholder="例如：研发 6:4"
              @input="dialogErrorField === 'name' && (dialogError = null)"
            />
            <p v-if="dialogErrorField === 'name' && dialogError" class="mt-1 text-xs text-destructive">{{ dialogError }}</p>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium">直属上级 (%)</label>
              <input
                v-model.number="formManagerPct"
                type="number"
                min="0"
                max="100"
                class="w-full rounded-md border px-3 py-2 text-sm tabular-nums"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium">虚线上级 (%)</label>
              <input
                v-model.number="formDottedPct"
                type="number"
                min="0"
                max="100"
                class="w-full rounded-md border px-3 py-2 text-sm tabular-nums"
              />
            </div>
          </div>
          <p class="text-xs text-muted-foreground">两项占比之和须为 100%，允许一侧为 0%、一侧为 100%；保存时按小数写入后端。</p>
          <p v-if="dialogErrorField === 'weights' && dialogError" class="text-xs text-destructive">{{ dialogError }}</p>
        </div>
        <p v-if="dialogErrorField === 'general' && dialogError" class="mt-3 text-sm text-destructive">{{ dialogError }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="closeDialog">取消</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
            :disabled="saving"
            @click="saveRule"
          >
            {{ saving ? "保存中..." : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
