import { request } from "./api";
import type { Page } from "./org-api";

/** One signed-in device, as the backend lists it. `current` is the one making this request. */
export type DeviceSession = {
  id: string;
  userAgent: string | null;
  ip: string | null;
  /** "City, Country" from the IP, or null when the server has no GeoIP database or does not know. */
  location: string | null;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
};

const SESSIONS_PATH = "/api/auth/sessions";

/** A person has a handful of devices, so one page of the maximum size shows them all. */
const SESSIONS_PAGE_SIZE = 100;

export const listSessions = (accessToken: string) =>
  request<Page<DeviceSession>>(`${SESSIONS_PATH}?size=${SESSIONS_PAGE_SIZE}`, {
    accessToken,
  });

export const revokeSession = (accessToken: string, sessionId: string) =>
  request<void>(`${SESSIONS_PATH}/${encodeURIComponent(sessionId)}`, {
    method: "DELETE",
    accessToken,
  });

export const revokeOtherSessions = (accessToken: string) =>
  request<void>(SESSIONS_PATH, { method: "DELETE", accessToken });
