import { request } from "./api";

export const changePassword = (
  accessToken: string,
  currentPassword: string,
  newPassword: string,
) =>
  request<void>("/api/auth/password", {
    method: "PUT",
    body: { currentPassword, newPassword },
    accessToken,
  });
