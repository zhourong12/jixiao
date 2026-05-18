import type { TodoItem } from "@/types/api.interface";
import { apiJson } from "./http";

function monthQuery(params?: { year?: number; month?: number }): string {
  if (params?.year == null && params?.month == null) return "";
  const q = new URLSearchParams();
  if (params.year != null) q.set("year", String(params.year));
  if (params.month != null) q.set("month", String(params.month));
  const s = q.toString();
  return s ? `?${s}` : "";
}

export async function getTodos(params?: { year?: number; month?: number }): Promise<{ items: TodoItem[] }> {
  return apiJson<{ items: TodoItem[] }>(`/api/home/todos${monthQuery(params)}`, {
    method: "GET",
  });
}
