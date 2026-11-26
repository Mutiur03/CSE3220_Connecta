import { request } from "./api";
import type { Page } from "./org-api";

/** One member with whether auto-assignment may pick them, whether they are online, and their open load. */
export type MemberAvailability = {
  membershipId: string;
  userId: string;
  email: string;
  name: string;
  role: "ADMIN" | "AGENT";
  /** Auto-assignment picks only assignable members, online or not. */
  assignable: boolean;
  /** Holds a live connection to the organization (or dropped one a moment ago). */
  online: boolean;
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

/** Admin only: lets auto-assignment pick this member, or stops it. */
export const setAssignable = (
  accessToken: string,
  orgId: string,
  membershipId: string,
  assignable: boolean,
) =>
  request<{ membershipId: string; assignable: boolean }>(
    `${orgPath(orgId)}/members/${encodeURIComponent(membershipId)}/assignable`,
    { method: "PUT", body: { assignable }, accessToken },
  );

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
