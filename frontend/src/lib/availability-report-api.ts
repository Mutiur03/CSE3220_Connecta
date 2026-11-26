import { request } from "./api";

export type AvailabilityDay = { date: string; availableSeconds: number };

/** One member's available time across the requested range; `days` has every date, zero-filled. */
export type MemberAvailabilityReport = {
  membershipId: string;
  userId: string;
  name: string;
  role: "ADMIN" | "AGENT";
  totalSeconds: number;
  days: AvailabilityDay[];
};

export type AvailabilityReport = {
  from: string;
  to: string;
  content: MemberAvailabilityReport[];
  page: number;
  size: number;
  totalElements: number;
};

/** Available time per member per UTC day, from and to inclusive (admin only, at most 31 days). */
export const getAvailabilityReport = (
  accessToken: string,
  orgId: string,
  from: string,
  to: string,
  size: number,
) =>
  request<AvailabilityReport>(
    `/api/organizations/${encodeURIComponent(orgId)}/availability/report?from=${from}&to=${to}&page=0&size=${size}`,
    { accessToken },
  );
