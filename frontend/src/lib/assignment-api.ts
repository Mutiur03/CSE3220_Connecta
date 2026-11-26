import { request } from "./api";
import type { Page } from "./org-api";

export type AvailabilityStatus = "AVAILABLE" | "AWAY";

/** One member with their availability and how many open conversations they hold. */
export type MemberAvailability = {
  membershipId: string;
  userId: string;
  email: string;
  name: string;
  role: "ADMIN" | "AGENT";
  status: AvailabilityStatus;
  /** ISO instant, null until the member first set a status. */
  updatedAt: string | null;
  openConversations: number;
};

/** The conversation's assignee, or null when unassigned. */
export type Assignee = {
  membershipId: string;
  userId: string;
  name: string;
};

export type AssignmentSource = "MANUAL" | "AUTO_ROUND_ROBIN";

type HistoryPerson = {
  membershipId: string | null;
  userId: string;
  name: string;
};

export type AssignmentRecord = {
  id: string;
  from: HistoryPerson | null;
  to: HistoryPerson | null;
  /** The admin who made the change; null for an automatic one. */
  assignedBy: HistoryPerson | null;
  source: AssignmentSource;
  createdAt: string;
};

const orgPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}`;
const conversationPath = (orgId: string, conversationId: string) =>
  `${orgPath(orgId)}/conversations/${encodeURIComponent(conversationId)}`;

/** Every member of the organization (admin only). */
export const listAvailability = (
  accessToken: string,
  orgId: string,
  page: number,
  size: number,
) =>
  request<Page<MemberAvailability>>(
    `${orgPath(orgId)}/availability?page=${page}&size=${size}`,
    { accessToken },
  );

export const getOwnAvailability = (accessToken: string, orgId: string) =>
  request<MemberAvailability>(`${orgPath(orgId)}/availability/me`, {
    accessToken,
  });

export const setOwnAvailability = (
  accessToken: string,
  orgId: string,
  status: AvailabilityStatus,
) =>
  request<MemberAvailability>(`${orgPath(orgId)}/availability/me`, {
    method: "PUT",
    body: { status },
    accessToken,
  });

/** Hands the conversation to a member; null unassigns (admin only). */
export const assignConversation = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  membershipId: string | null,
) =>
  request<{ conversationId: string; membershipId: string | null }>(
    `${conversationPath(orgId, conversationId)}/assignment`,
    { method: "POST", body: { membershipId }, accessToken },
  );

/** Newest hand-overs first (admin only). */
export const listAssignments = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  page: number,
  size: number,
) =>
  request<Page<AssignmentRecord>>(
    `${conversationPath(orgId, conversationId)}/assignments?page=${page}&size=${size}`,
    { accessToken },
  );
