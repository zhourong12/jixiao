<script setup lang="ts">
import { computed, ref, watch } from "vue";
import * as XLSX from "xlsx";
import type { CreateEmployeeRequest, FeishuSubjectOption } from "@/types/api.interface";
import { createEmployee, subjectCodeQueryValue } from "@/api/employees";

const props = withDefaults(
  defineProps<{
    open: boolean;
    subjects?: FeishuSubjectOption[];
    /** 打开弹窗时优先选中的主体 code（如列表筛选）；空则选第一个主体 */
    defaultSubjectCode?: string;
  }>(),
  { subjects: () => [], defaultSubjectCode: "" },
);

const emit = defineEmits<{
  close: [];
  success: [];
}>();

interface RosterRow {
  name: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  departmentName?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  managerName?: string;
  dottedManagerName?: string;
}

interface OpenIdRow {
  name: string;
  openId: string;
}

interface MatchedRow extends RosterRow {
  openId: string;
  matchStatus: "matched" | "unmatched";
}

const rosterFileName = ref("");
const openIdFileName = ref("");
const rosterData = ref<RosterRow[]>([]);
const openIdData = ref<OpenIdRow[]>([]);
const matchedData = ref<MatchedRow[]>([]);
const importing = ref(false);
const importProgress = ref({ current: 0, total: 0 });
const message = ref<string | null>(null);

const rosterInput = ref<HTMLInputElement | null>(null);
const openIdInput = ref<HTMLInputElement | null>(null);

/** 本次导入绑定的飞书主体（必填） */
const importSubjectCode = ref("");

function syncImportSubjectFromProps() {
  const subs = props.subjects || [];
  if (subs.length === 0) {
    importSubjectCode.value = "";
    return;
  }
  const def = subjectCodeQueryValue(props.defaultSubjectCode);
  if (def && subs.some((s) => s.code === def)) {
    importSubjectCode.value = def;
  } else {
    importSubjectCode.value = subjectCodeQueryValue(subs[0]?.code) || "";
  }
}

watch([() => props.open, () => props.subjects], () => {
  if (props.open) syncImportSubjectFromProps();
});

const matchedCount = computed(() => matchedData.value.filter((row) => row.matchStatus === "matched").length);
const unmatchedCount = computed(() => matchedData.value.length - matchedCount.value);

function resetState() {
  rosterFileName.value = "";
  openIdFileName.value = "";
  rosterData.value = [];
  openIdData.value = [];
  matchedData.value = [];
  importProgress.value = { current: 0, total: 0 };
  message.value = null;
  if (rosterInput.value) rosterInput.value.value = "";
  if (openIdInput.value) openIdInput.value.value = "";
}

function closeDialog() {
  resetState();
  emit("close");
}

function parseRosterRows(jsonData: Record<string, unknown>[]): RosterRow[] {
  const mappings: Record<string, keyof RosterRow> = {
    姓名: "name",
    手机号码: "phone",
    工号: "employeeNo",
    人员类型: "employeeType",
    部门: "departmentName",
    职务: "position",
    工作地点: "workLocation",
    入职日期: "joinDate",
    直属上级: "managerName",
    虚线上级: "dottedManagerName",
  };

  return jsonData
    .map((row) => {
      const result: Partial<RosterRow> = {};
      for (const [cnKey, enKey] of Object.entries(mappings)) {
        const val = row[cnKey];
        if (val === undefined || val === null) continue;
        let strVal = String(val).trim();
        if (enKey === "joinDate" && typeof val === "number") {
          const date = XLSX.SSF.parse_date_code(val);
          if (date) {
            strVal = `${date.y}-${String(date.m).padStart(2, "0")}-${String(date.d).padStart(2, "0")}`;
          }
        }
        result[enKey] = strVal;
      }
      return result as RosterRow;
    })
    .filter((row) => row.name);
}

function parseOpenIdRows(jsonData: Record<string, unknown>[]): OpenIdRow[] {
  return jsonData
    .map((row) => ({
      name: String(row["姓名"] || row.name || "").trim(),
      openId: String(row["飞书open_id"] || row.open_id || row.openId || "").trim(),
    }))
    .filter((row) => row.name && row.openId);
}

function performMatching(roster: RosterRow[], openIds: OpenIdRow[]) {
  const openIdMap = new Map<string, string>();
  openIds.forEach((item) => openIdMap.set(item.name, item.openId));
  matchedData.value = roster.map((row) => {
    const openId = openIdMap.get(row.name);
    return {
      ...row,
      openId: openId || "",
      matchStatus: openId ? "matched" : "unmatched",
    };
  });
  message.value = `匹配完成：成功 ${matchedCount.value} 条，未匹配 ${unmatchedCount.value} 条`;
}

async function readWorkbook(file: File): Promise<Record<string, unknown>[]> {
  const buffer = await file.arrayBuffer();
  const workbook = XLSX.read(buffer, { type: "array" });
  const firstSheet = workbook.Sheets[workbook.SheetNames[0]];
  return XLSX.utils.sheet_to_json(firstSheet) as Record<string, unknown>[];
}

async function onRosterFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  rosterFileName.value = file.name;
  try {
    const rows = parseRosterRows(await readWorkbook(file));
    rosterData.value = rows;
    message.value = `花名册解析成功，共 ${rows.length} 条数据`;
    if (openIdData.value.length > 0) performMatching(rows, openIdData.value);
  } catch {
    rosterData.value = [];
    message.value = "解析花名册失败，请检查文件格式";
  }
}

async function onOpenIdFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  openIdFileName.value = file.name;
  try {
    const rows = parseOpenIdRows(await readWorkbook(file));
    openIdData.value = rows;
    message.value = `OpenID 清单解析成功，共 ${rows.length} 条数据`;
    if (rosterData.value.length > 0) performMatching(rosterData.value, rows);
  } catch {
    openIdData.value = [];
    message.value = "解析 OpenID 清单失败，请检查文件格式";
  }
}

async function importMatched() {
  const subjectCode = subjectCodeQueryValue(importSubjectCode.value);
  if (!subjectCode) {
    message.value = "请先选择飞书主体";
    return;
  }
  if (matchedData.value.length === 0) {
    message.value = "没有可导入的数据";
    return;
  }
  const matchedOnly = matchedData.value.filter((row) => row.matchStatus === "matched");
  if (matchedOnly.length === 0) {
    message.value = "没有成功匹配的数据可导入";
    return;
  }

  importing.value = true;
  importProgress.value = { current: 0, total: matchedOnly.length };
  const nameToOpenId = new Map<string, string>();
  matchedData.value.forEach((row) => {
    if (row.openId) nameToOpenId.set(row.name, row.openId);
  });

  let successCount = 0;
  let failCount = 0;
  for (let i = 0; i < matchedOnly.length; i++) {
    const row = matchedOnly[i];
    try {
      const body: CreateEmployeeRequest = {
        userId: row.openId,
        feishuOpenId: row.openId,
        feishuSubjectCode: subjectCode,
        name: row.name,
        phone: row.phone || undefined,
        employeeNo: row.employeeNo || undefined,
        employeeType: row.employeeType || undefined,
        departmentName: row.departmentName || undefined,
        position: row.position || undefined,
        workLocation: row.workLocation || undefined,
        joinDate: row.joinDate || undefined,
        managerId: row.managerName ? nameToOpenId.get(row.managerName) : undefined,
        dottedManagerId: row.dottedManagerName ? nameToOpenId.get(row.dottedManagerName) : undefined,
      };
      await createEmployee(body);
      successCount++;
    } catch {
      failCount++;
    }
    importProgress.value = { current: i + 1, total: matchedOnly.length };
  }

  importing.value = false;
  message.value =
    failCount === 0
      ? `全部导入成功，共 ${successCount} 条`
      : `导入完成：成功 ${successCount} 条，失败 ${failCount} 条`;
  emit("success");
  closeDialog();
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" @click.self="closeDialog">
    <div class="flex max-h-[90vh] w-full max-w-5xl flex-col overflow-hidden rounded-md border bg-card shadow-lg">
      <div class="border-b px-6 py-4">
        <h2 class="text-lg font-semibold">导入花名册（匹配飞书 OpenID）</h2>
      </div>
      <div class="space-y-6 overflow-y-auto px-6 py-4">
        <p v-if="message" class="text-sm text-muted-foreground">{{ message }}</p>
        <div v-if="!subjects.length" class="rounded-md border border-warning/30 bg-warning-bg px-4 py-3 text-sm text-warning">
          暂无可用飞书主体，无法导入。请确认已登录并完成飞书主体配置。
        </div>
        <div v-else class="rounded-md border p-4">
          <label class="mb-2 block text-sm font-medium text-muted-foreground">飞书主体（导入的员工均归属此主体）</label>
          <select
            v-model="importSubjectCode"
            class="w-full max-w-md rounded-md border px-3 py-2 text-sm"
            :disabled="importing"
          >
            <option v-for="s in subjects" :key="s.code" :value="s.code">{{ s.name }}（{{ s.code }}）</option>
          </select>
        </div>
        <div class="grid gap-4 md:grid-cols-2">
          <div class="space-y-3 rounded-md border p-4">
            <p class="text-sm font-medium">1. 花名册文件</p>
            <input
              ref="rosterInput"
              type="file"
              accept=".xlsx,.xls"
              class="hidden"
              :disabled="importing || !subjects.length"
              @change="onRosterFileChange"
            />
            <button
              type="button"
              class="w-full rounded-md border px-3 py-2 text-sm disabled:opacity-50"
              :disabled="importing || !subjects.length"
              @click="rosterInput?.click()"
            >
              {{ rosterFileName ? "重新选择" : "选择花名册" }}
            </button>
            <p v-if="rosterFileName" class="text-xs text-muted-foreground">
              {{ rosterFileName }}（{{ rosterData.length }} 条）
            </p>
          </div>
          <div class="space-y-3 rounded-md border p-4">
            <p class="text-sm font-medium">2. 飞书 OpenID 清单</p>
            <input
              ref="openIdInput"
              type="file"
              accept=".xlsx,.xls"
              class="hidden"
              :disabled="importing || !subjects.length"
              @change="onOpenIdFileChange"
            />
            <button
              type="button"
              class="w-full rounded-md border px-3 py-2 text-sm disabled:opacity-50"
              :disabled="importing || !subjects.length"
              @click="openIdInput?.click()"
            >
              {{ openIdFileName ? "重新选择" : "选择 OpenID 清单" }}
            </button>
            <p v-if="openIdFileName" class="text-xs text-muted-foreground">
              {{ openIdFileName }}（{{ openIdData.length }} 条）
            </p>
          </div>
        </div>
        <div v-if="matchedData.length > 0" class="overflow-x-auto rounded-md border">
          <table class="w-full min-w-[960px] text-left text-sm">
            <thead class="border-b bg-muted/40">
              <tr>
                <th class="px-3 py-2">匹配状态</th>
                <th class="px-3 py-2">姓名</th>
                <th class="px-3 py-2">飞书 OpenID</th>
                <th class="px-3 py-2">手机号</th>
                <th class="px-3 py-2">工号</th>
                <th class="px-3 py-2">部门</th>
                <th class="px-3 py-2">直属上级</th>
                <th class="px-3 py-2">虚线上级</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in matchedData" :key="`${row.name}-${index}`" class="border-b">
                <td class="px-3 py-2">
                  <span
                    class="rounded-full px-2 py-0.5 text-xs"
                    :class="row.matchStatus === 'matched' ? 'bg-success/10 text-success' : 'bg-warning/10 text-warning'"
                  >
                    {{ row.matchStatus === "matched" ? "已匹配" : "未匹配" }}
                  </span>
                </td>
                <td class="px-3 py-2">{{ row.name }}</td>
                <td class="px-3 py-2 font-mono text-xs">{{ row.openId || "—" }}</td>
                <td class="px-3 py-2">{{ row.phone || "—" }}</td>
                <td class="px-3 py-2">{{ row.employeeNo || "—" }}</td>
                <td class="px-3 py-2">{{ row.departmentName || "—" }}</td>
                <td class="px-3 py-2">{{ row.managerName || "—" }}</td>
                <td class="px-3 py-2">{{ row.dottedManagerName || "—" }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="importing" class="text-sm text-muted-foreground">
          导入中 {{ importProgress.current }} / {{ importProgress.total }}
        </p>
      </div>
      <div class="flex justify-end gap-2 border-t px-6 py-4">
        <button type="button" class="rounded-md border px-4 py-2 text-sm" :disabled="importing" @click="resetState">
          重置
        </button>
        <button type="button" class="rounded-md border px-4 py-2 text-sm" :disabled="importing" @click="closeDialog">
          取消
        </button>
        <button
          type="button"
          class="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
          :disabled="importing || !subjects.length || !subjectCodeQueryValue(importSubjectCode) || matchedCount === 0"
          @click="importMatched"
        >
          {{ importing ? "导入中…" : `导入已匹配（${matchedCount}）` }}
        </button>
      </div>
    </div>
  </div>
</template>
