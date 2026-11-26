import { request } from "./api";
import type { Page } from "./org-api";

/** One account in the system owner's user list. */
export type AdminUser = {
  id: string;
  email: string;
  fullName: string;
  enabled: boolean;
  roles: string[];
};

/** Every user account. Only the system owner may call this. */
export const listUsers = (accessToken: string, page: number, size: number) =>
  request<Page<AdminUser>>(`/api/admin/users?page=${page}&size=${size}`, {
    accessToken,
  });

export const setUserEnabled = (
  accessToken: string,
  userId: string,
  enabled: boolean,
) =>
  request<AdminUser>(`/api/admin/users/${encodeURIComponent(userId)}`, {
    method: "PATCH",
    body: { enabled },
    accessToken,
  });
