export function formatPeriodDisplay(period?: string): string {
  const value = period?.trim();
  if (!value) return "-";
  const monthMatch = /^(\d{4})-(\d{1,2})$/.exec(value);
  if (monthMatch) {
    const month = Number(monthMatch[2]);
    if (month >= 1 && month <= 12) return `${monthMatch[1]}年${month}月`;
  }
  const quarterMatch = /^(\d{4})-Q([1-4])$/i.exec(value);
  if (quarterMatch) return `${quarterMatch[1]}年Q${quarterMatch[2]}`;
  if (value.length <= 24) return value;
  return `${value.slice(0, 14)}…${value.slice(-8)}`;
}

export function periodOptionLabel(periodKey: string, name?: string): string {
  const label = name?.trim();
  if (label) return label;
  return formatPeriodDisplay(periodKey);
}
