<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type {
  CultureDimensionDef,
  PerformanceIndicator,
  PerformanceTemplate,
  TemplateListItem,
  UpdateTemplateRequest,
} from "@/types/api.interface";
import { defaultCultureDimensions } from "@/types/api.interface";
import { createTemplate, deleteTemplate, getTemplateDetail, listTemplates, toggleTemplateStatus, updateTemplate } from "@/api/templates";
import ListPagination from "@/components/ui/ListPagination.vue";
import { formatDateTime } from "@/utils/datetime";

const items = ref<TemplateListItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const loading = ref(true);
const message = ref<string | null>(null);
/** 弹窗内校验/保存错误（列表上方 message 仅用于列表区操作） */
const dialogError = ref<string | null>(null);
type DialogErrorField = "name" | "culture" | "indicators" | "general";
const dialogErrorField = ref<DialogErrorField | null>(null);
const filterType = ref("");
const dialogOpen = ref(false);
const formName = ref("");
const formPosition = ref("");
const formType = ref<"performance" | "culture" | "learning">("performance");
const indicators = ref<PerformanceIndicator[]>([{ name: "", weight: 100, description: "", maxScore: 100, criteria: "" }]);
const cultureDimensions = ref<CultureDimensionDef[]>(defaultCultureDimensions());
const saving = ref(false);
/** 从列表「复制」打开弹窗时，记录源模板名称，用于提示文案 */
const dialogCopySourceName = ref<string | null>(null);
/** 弹窗为「编辑」时已存在模板 id；新建 / 从列表复制 时为 null */
const editingTemplateId = ref<string | null>(null);

/** 弹窗内各指标「权重(%)」之和，用于与模板总权重上限对照 */
const dialogWeightSum = computed(() =>
  indicators.value.reduce((s, i) => s + (Number.isFinite(Number(i.weight)) ? Number(i.weight) : 0), 0),
);

const dialogCultureMaxSum = computed(() =>
  cultureDimensions.value.reduce((s, r) => s + (Number.isFinite(Number(r.maxScore)) ? Number(r.maxScore) : 0), 0),
);

async function load() {
  loading.value = true;
  try {
    const res = await listTemplates(page.value, pageSize.value, filterType.value || undefined);
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

function clearDialogError() {
  dialogError.value = null;
  dialogErrorField.value = null;
}

function setDialogError(field: DialogErrorField, msg: string) {
  dialogErrorField.value = field;
  dialogError.value = msg;
}

function openCreate() {
  editingTemplateId.value = null;
  dialogCopySourceName.value = null;
  formName.value = "";
  formPosition.value = "";
  formType.value = "performance";
  indicators.value = [{ name: "", weight: 100, description: "", maxScore: 100, criteria: "" }];
  cultureDimensions.value = defaultCultureDimensions();
  clearDialogError();
  dialogOpen.value = true;
}

function addIndicator() {
  indicators.value.push({ name: "", weight: 0, description: "", maxScore: 100, criteria: "" });
}

function addCultureRow() {
  cultureDimensions.value.push({ name: "", maxScore: 0, description: "", criteria: "" });
}

function closeDialog() {
  dialogOpen.value = false;
  dialogCopySourceName.value = null;
  editingTemplateId.value = null;
  clearDialogError();
}

async function saveTemplate() {
  const type = formType.value;
  const name = formName.value.trim();
  const position = formPosition.value.trim();
  clearDialogError();
  if (!name) {
    setDialogError("name", "请填写模板名称");
    return;
  }
  if (type === "culture") {
    const cdSum = dialogCultureMaxSum.value;
    if (cdSum <= 0) {
      setDialogError("culture", `各维「满分」之和须大于 0（当前合计 ${cdSum}）`);
      return;
    }
    const seen = new Set<string>();
    for (const row of cultureDimensions.value) {
      const n = row.name.trim();
      if (!n) {
        setDialogError("culture", "维度名称不能为空");
        return;
      }
      if (seen.has(n)) {
        setDialogError("culture", "维度名称不能重复");
        return;
      }
      seen.add(n);
    }
  } else {
    const indicatorsPayload = indicators.value.filter((i) => i.name.trim());
    if (indicatorsPayload.length === 0) {
      setDialogError("indicators", "请至少添加一项指标");
      return;
    }
    const weightSum = indicatorsPayload.reduce((sum, item) => sum + Number(item.weight || 0), 0);
    if (weightSum <= 0) {
      setDialogError("indicators", "指标权重合计须大于 0");
      return;
    }
  }
  saving.value = true;
  try {
    const indicatorsPayload =
      type === "performance" || type === "learning" ? indicators.value.filter((i) => i.name.trim()) : [];
    const culturePayload = cultureDimensions.value.map((d) => ({
      name: d.name.trim(),
      maxScore: Number(d.maxScore) || 0,
      description: d.description ?? "",
      criteria: d.criteria ?? "",
    }));
    if (editingTemplateId.value) {
      const patch: UpdateTemplateRequest = {
        name,
        position,
        type,
      };
      if (type === "performance" || type === "learning") {
        patch.indicators = indicatorsPayload;
      }
      if (type === "culture") {
        patch.cultureDimensions = culturePayload;
      }
      await updateTemplate(editingTemplateId.value, patch);
    } else {
      await createTemplate({
        name,
        position,
        type,
        indicators: indicatorsPayload,
        cultureDimensions: type === "culture" ? culturePayload : undefined,
      });
    }
    closeDialog();
    await load();
  } catch (e) {
    setDialogError("general", e instanceof Error ? e.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggle(row: TemplateListItem) {
  try {
    await toggleTemplateStatus(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "操作失败";
  }
}

async function copy(row: TemplateListItem) {
  message.value = null;
  editingTemplateId.value = null;
  try {
    const detail = await getTemplateDetail(row.id);
    formName.value = `${detail.name} 副本`;
    formPosition.value = detail.position;
    formType.value = detail.type || "performance";
    if (formType.value === "culture") {
      indicators.value = [];
    } else {
      indicators.value = mapDetailToIndicators(detail);
    }
    cultureDimensions.value =
      formType.value === "culture" ? mapDetailCultureDimensions(detail) : defaultCultureDimensions();
    clearDialogError();
    dialogOpen.value = true;
  } catch (e) {
    message.value = e instanceof Error ? e.message : "加载模板失败";
  }
}

function mapDetailToIndicators(detail: { indicators: PerformanceIndicator[] }): PerformanceIndicator[] {
  return detail.indicators.length > 0
    ? detail.indicators.map((i) => ({
        name: i.name,
        weight: Number.isFinite(Number(i.weight)) ? Number(i.weight) : 0,
        description: i.description ?? "",
        maxScore: typeof i.maxScore === "number" && !Number.isNaN(i.maxScore) && i.maxScore > 0 ? i.maxScore : 100,
        criteria: i.criteria ?? "",
      }))
    : [{ name: "", weight: 100, description: "", maxScore: 100, criteria: "" }];
}

function mapDetailCultureDimensions(detail: PerformanceTemplate): CultureDimensionDef[] {
  const raw = detail.cultureDimensions;
  if (raw && raw.length > 0) {
    return raw.map((d) => ({
      name: (d.name ?? "").trim(),
      maxScore: Number.isFinite(Number(d.maxScore)) ? Number(d.maxScore) : 0,
      description: d.description ?? "",
      criteria: d.criteria ?? "",
    }));
  }
  return defaultCultureDimensions();
}

async function edit(row: TemplateListItem) {
  message.value = null;
  dialogCopySourceName.value = null;
  try {
    const detail = await getTemplateDetail(row.id);
    editingTemplateId.value = detail.id;
    formName.value = detail.name;
    formPosition.value = detail.position;
    formType.value = detail.type || "performance";
    if (formType.value === "culture") {
      indicators.value = [];
      cultureDimensions.value = mapDetailCultureDimensions(detail);
    } else {
      indicators.value = mapDetailToIndicators(detail);
      cultureDimensions.value = defaultCultureDimensions();
    }
    clearDialogError();
    dialogOpen.value = true;
  } catch (e) {
    message.value = e instanceof Error ? e.message : "加载模板失败";
  }
}

async function remove(row: TemplateListItem) {
  if (!window.confirm(`确定删除模板「${row.name}」吗？`)) return;
  try {
    await deleteTemplate(row.id);
    await load();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "删除失败";
  }
}

onMounted(() => {
  void load();
});
watch([page, pageSize], () => void load());
watch(filterType, () => {
  page.value = 1;
  void load();
});
watch(formType, (t) => {
  if (!dialogOpen.value || editingTemplateId.value) return;
  if (t === "performance" || t === "learning") {
    indicators.value = [{ name: "", weight: 100, description: "", maxScore: 100, criteria: "" }];
    cultureDimensions.value = defaultCultureDimensions();
  } else {
    indicators.value = [];
    cultureDimensions.value = defaultCultureDimensions();
  }
});
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-center gap-3">
        <select v-model="filterType" class="rounded-md border px-3 py-2 text-sm">
          <option value="">全部类型</option>
          <option value="performance">绩效</option>
          <option value="culture">文化价值观</option>
          <option value="learning">学习与成长</option>
        </select>
      </div>
      <button type="button" class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" @click="openCreate">
        新建模板
      </button>
    </div>
    <p v-if="message && !dialogOpen" class="text-sm text-destructive">{{ message }}</p>
    <section class="ui-list-panel">
      <div v-if="loading" class="ui-loading">加载中...</div>
      <div v-else class="ui-table-wrap">
        <table class="ui-table min-w-[720px]">
          <thead>
            <tr>
              <th>名称</th>
              <th>类型</th>
              <th>岗位</th>
              <th>指标数</th>
              <th>状态</th>
              <th>版本</th>
              <th>创建时间</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in items" :key="row.id">
              <td>{{ row.name }}</td>
              <td>{{ { performance: "绩效", culture: "文化价值观", learning: "学习与成长" }[row.type || "performance"] || row.type }}</td>
              <td>{{ row.position }}</td>
              <td>{{ row.indicatorCount }}</td>
              <td>{{ row.status === "enabled" ? "启用" : "停用" }}</td>
              <td>{{ row.version }}</td>
              <td class="text-muted-foreground">{{ formatDateTime(row.createdAt) }}</td>
              <td class="text-right">
              <button type="button" class="mr-2 text-primary hover:underline" @click="edit(row)">编辑</button>
              <button type="button" class="mr-2 text-primary hover:underline" @click="toggle(row)">启用/停用</button>
              <button type="button" class="mr-2 text-primary hover:underline" @click="copy(row)">复制</button>
              <button type="button" class="text-destructive hover:underline" @click="remove(row)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <ListPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </section>
    <div
      v-if="dialogOpen"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      @click.self="closeDialog"
    >
      <div class="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-md border bg-card p-6 shadow-lg">
        <h2 class="text-lg font-semibold">{{ editingTemplateId ? "编辑模板" : "新建模板" }}</h2>
        <p v-if="dialogCopySourceName" class="mt-1 text-sm text-primary">
          已从「{{ dialogCopySourceName }}」载入，可在下方修改后保存为新模板（不会改动原模板）。
        </p>
        <div class="mt-4 space-y-3">
          <div>
            <label class="mb-1 block text-xs font-medium text-foreground">模板名称</label>
            <input
              v-model="formName"
              class="w-full rounded-md border px-3 py-2 text-sm"
              :class="dialogErrorField === 'name' ? 'border-destructive' : ''"
              placeholder="模板名称"
              @input="dialogErrorField === 'name' && clearDialogError()"
            />
            <p v-if="dialogErrorField === 'name' && dialogError" class="mt-1 text-xs text-destructive">{{ dialogError }}</p>
          </div>
          <div class="flex gap-3">
            <select v-model="formType" class="rounded-md border px-3 py-2 text-sm" :disabled="!!editingTemplateId">
              <option value="performance">绩效</option>
              <option value="culture">文化价值观</option>
              <option value="learning">学习与成长</option>
            </select>
            <input
              v-if="formType === 'performance' || formType === 'learning'"
              v-model="formPosition"
              class="flex-1 rounded-md border px-3 py-2 text-sm"
              placeholder="适用岗位"
            />
          </div>
          <div
            v-if="formType === 'performance' || formType === 'learning'"
            v-for="(ind, idx) in indicators"
            :key="idx"
            class="space-y-3 rounded-md border border-border p-3"
          >
            <div class="flex flex-wrap items-end gap-3">
              <div class="min-w-[8rem] flex-1 basis-[40%]">
                <label class="mb-1 block text-xs font-medium text-foreground">指标名</label>
                <input v-model="ind.name" class="w-full rounded-md border px-2 py-1.5 text-sm" placeholder="例如：业绩目标" />
              </div>
              <div class="w-[5.5rem] shrink-0">
                <label class="mb-1 block text-xs font-medium text-foreground">权重 (%)</label>
                <input
                  v-model.number="ind.weight"
                  type="number"
                  min="0"
                  max="100"
                  step="1"
                  class="w-full rounded-md border px-2 py-1.5 text-sm"
                  title="模板内权重（%），设定目标时须与评分方案绩效占比一致"
                />
              </div>
              <div class="w-[6.5rem] shrink-0">
                <label class="mb-1 block text-xs font-medium text-foreground">单项最高分</label>
                <input
                  v-model.number="ind.maxScore"
                  type="number"
                  min="1"
                  max="100"
                  step="1"
                  class="w-full rounded-md border px-2 py-1.5 text-sm"
                  title="该指标评分上限"
                />
              </div>
              <button
                v-if="indicators.length > 1"
                type="button"
                class="mb-0.5 shrink-0 text-xs text-destructive hover:underline"
                @click="indicators.splice(idx, 1)"
              >
                删除
              </button>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-foreground">衡量标准（评分依据）</label>
              <textarea
                v-model="ind.criteria"
                rows="6"
                class="min-h-[9rem] w-full resize-y rounded-md border px-2 py-1.5 text-sm"
                placeholder="评分时参考的描述"
              />
            </div>
          </div>
          <div v-if="formType === 'culture'" class="mt-6 border-t border-border pt-6">
            <h3 class="text-sm font-semibold text-foreground">文化价值观维度</h3>
            <p class="mt-1 text-xs text-muted-foreground">
              各维「满分」之和须大于 0；创建绩效时将写入记录快照。
            </p>
            <p v-if="dialogErrorField === 'culture' && dialogError" class="mt-2 text-xs text-destructive">{{ dialogError }}</p>
            <div v-for="(cd, cidx) in cultureDimensions" :key="cidx" class="mt-3 space-y-2 rounded-md border border-border p-3">
              <div class="flex flex-wrap items-end gap-3">
                <div class="min-w-[6rem] flex-1 basis-[35%]">
                  <label class="mb-1 block text-xs font-medium text-foreground">维度名称</label>
                  <input v-model="cd.name" class="w-full rounded-md border px-2 py-1.5 text-sm" placeholder="例如：利他" />
                </div>
                <div class="w-[5.5rem] shrink-0">
                  <label class="mb-1 block text-xs font-medium text-foreground">满分</label>
                  <input
                    v-model.number="cd.maxScore"
                    type="number"
                    min="0"
                    step="1"
                    class="w-full rounded-md border px-2 py-1.5 text-sm"
                  />
                </div>
                <button
                  v-if="cultureDimensions.length > 1"
                  type="button"
                  class="mb-0.5 shrink-0 text-xs text-destructive hover:underline"
                  @click="cultureDimensions.splice(cidx, 1)"
                >
                  删除
                </button>
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-foreground">说明</label>
                <textarea v-model="cd.description" rows="3" class="w-full resize-y rounded-md border px-2 py-1.5 text-sm" placeholder="说明" />
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-foreground">评分标准</label>
                <textarea v-model="cd.criteria" rows="2" class="w-full resize-y rounded-md border px-2 py-1.5 text-sm" placeholder="评分档说明" />
              </div>
            </div>
            <p class="mt-2 text-sm text-muted-foreground">
              各维满分当前合计 <span class="font-semibold tabular-nums">{{ dialogCultureMaxSum }}</span>
            </p>
            <button type="button" class="mt-1 text-sm text-primary hover:underline" @click="addCultureRow">+ 添加维度</button>
          </div>
          <template v-if="formType === 'performance' || formType === 'learning'">
            <p class="text-sm text-muted-foreground">
              「权重(%)」列当前合计 <span class="font-semibold tabular-nums">{{ dialogWeightSum }}%</span>（设定目标时须与评分方案绩效占比一致）。
            </p>
            <p v-if="dialogErrorField === 'indicators' && dialogError" class="text-xs text-destructive">{{ dialogError }}</p>
            <button type="button" class="text-sm text-primary hover:underline" @click="addIndicator">+ 添加指标</button>
          </template>
        </div>
        <p v-if="dialogErrorField === 'general' && dialogError" class="mt-3 text-sm text-destructive">{{ dialogError }}</p>
        <div class="mt-6 flex justify-end gap-2">
          <button type="button" class="rounded-md border px-4 py-2 text-sm" @click="closeDialog">取消</button>
          <button
            type="button"
            class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground"
            :disabled="saving"
            @click="saveTemplate"
          >
            {{ saving ? "保存中..." : editingTemplateId ? "保存修改" : "保存" }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
