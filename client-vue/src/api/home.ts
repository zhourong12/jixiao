import type { HomeActionCounts, PerformanceOverview, TodoItem } from "@/types/api.interface";
import { apiJson } from "./http";

function monthQuery(year?: number, month?: number): string {
  if (year == null || month == null) return "";
  const q = new URLSearchParams();
  q.set("year", String(year));
  q.set("month", String(month));
  return `?${q.toString()}`;
}

export async function getTodos(params?: { year?: number; month?: number }): Promise<{ items: TodoItem[] }> {
  return apiJson<{ items: TodoItem[] }>(`/api/home/todos${monthQuery(params?.year, params?.month)}`, {
    method: "GET",
  });
}

export async function getOverview(params?: { year?: number; month?: number }): Promise<PerformanceOverview> {
  return apiJson<PerformanceOverview>(`/api/home/overview${monthQuery(params?.year, params?.month)}`, {
    method: "GET",
  });
}

export async function getHomeActionCounts(params?: {
  year?: number;
  month?: number;
}): Promise<HomeActionCounts> {
  return apiJson<HomeActionCounts>(`/api/home/action-counts${monthQuery(params?.year, params?.month)}`, {
    method: "GET",
  });
}
