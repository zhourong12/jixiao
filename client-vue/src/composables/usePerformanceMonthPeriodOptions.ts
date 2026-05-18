import { computed, ref } from "vue";
import { getPerformanceFilterMonthPeriods } from "@/api/performances";
import { periodOptionLabel } from "@/utils/period";

export type PerformanceMonthPeriodItem = { periodKey: string; name: string };

export function usePerformanceMonthPeriodOptions() {
  const periodItems = ref<PerformanceMonthPeriodItem[]>([]);
  const periodLoading = ref(false);

  const periodSelectOptions = computed(() =>
    periodItems.value.map((p) => ({
      value: p.periodKey,
      label: periodOptionLabel(p.periodKey, p.name),
    })),
  );

  async function loadPeriodOptions() {
    periodLoading.value = true;
    try {
      const res = await getPerformanceFilterMonthPeriods();
      periodItems.value = (res.items ?? []).map((p) => ({
        periodKey: p.periodKey,
        name: p.name ?? "",
      }));
    } catch {
      periodItems.value = [];
    } finally {
      periodLoading.value = false;
    }
  }

  return { periodItems, periodSelectOptions, periodLoading, loadPeriodOptions };
}
