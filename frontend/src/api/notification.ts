import { API_BASE_URL } from "./config";

import type {
  NotificationPageResponse,
  NotificationResponse,
  UnreadNotificationCountResponse,
} from "../types/notification";

export async function getMyNotifications(
  accessToken: string,
  page = 0,
  size = 20
): Promise<NotificationPageResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/notifications?page=${page}&size=${size}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );

  if (response.status === 401) {
    throw new Error("Your session has expired. Please log in again.");
  }

  if (response.status === 400) {
    throw new Error("Invalid notification request.");
  }

  if (!response.ok) {
    throw new Error("Failed to load notifications.");
  }

  return (await response.json()) as NotificationPageResponse;
}

export async function getUnreadNotificationCount(
  accessToken: string
): Promise<number> {
  const response = await fetch(
    `${API_BASE_URL}/api/notifications/unread-count`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );

  if (response.status === 401) {
    throw new Error("Your session has expired. Please log in again.");
  }

  if (!response.ok) {
    throw new Error("Failed to load unread notification count.");
  }

  const data =
    (await response.json()) as UnreadNotificationCountResponse;

  return data.unreadCount;
}

export async function markNotificationAsRead(
  accessToken: string,
  notificationId: string
): Promise<NotificationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/notifications/${notificationId}/read`,
    {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );

  if (response.status === 401) {
    throw new Error("Your session has expired. Please log in again.");
  }

  if (response.status === 404) {
    throw new Error("Notification not found.");
  }

  if (!response.ok) {
    throw new Error("Failed to mark notification as read.");
  }

  return (await response.json()) as NotificationResponse;
}