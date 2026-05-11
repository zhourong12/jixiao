import type {
  NotificationListItem,
  SendNotificationRequest,
  SendNotificationResponse,
} from "@/types/api.interface";
import { apiJson } from "./http";

export async function getNotifications(params: { page?: number; pageSize?: number }): Promise<{
  items: NotificationListItem[];
  total: number;
  page: number;
  pageSize: number;
}> {
  const q = new URLSearchParams();
  if (params.page != null) q.set("page", String(params.page));
  if (params.pageSize != null) q.set("pageSize", String(params.pageSize));
  const suffix = q.toString() ? `?${q.toString()}` : "";
  return apiJson(`/api/admin/notifications${suffix}`, { method: "GET" });
}

export async function sendNotification(body: SendNotificationRequest): Promise<SendNotificationResponse> {
  return apiJson<SendNotificationResponse>("/api/admin/notifications", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
