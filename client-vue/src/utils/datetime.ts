/** 将 ISO 时间戳格式化为本地可读时间（列表/导出展示用） */
export function formatDateTime(value?: string | null): string {
  const raw = value?.trim();
  if (!raw) return "-";
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  return d.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}
