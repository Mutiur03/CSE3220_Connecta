import { request } from "./api";
import type { InboxChannel } from "./inbox-view";
import type { OrgRole } from "./org-permissions";

// The shapes in docs/analytics-metrics.md. Durations are whole seconds, null when there is nothing to measure.

/** The range the backend answered for: both dates inclusive, days cut in `zone`. */
export type AnalyticsRange = { from: string; to: string; zone: string };

export type ConversationAnalytics = AnalyticsRange & {
  started: number;
  /** Every date of the range, zero-filled. */
  startedPerDay: { date: string; count: number }[];
  startedPerChannel: { channel: InboxChannel; count: number }[];
  /** A snapshot of all conversations, not limited by the range. */
  statusNow: { open: number; closed: number };
  messagesReceived: number;
  messagesSent: number;
  medianFirstResponseSeconds: number | null;
  unanswered: number;
};

export type AgentAnalytics = {
  membershipId: string;
  name: string;
  role: OrgRole;
  assigned: number;
  resolved: number;
  openNow: number;
  repliesSent: number;
  availableSeconds: number;
  medianFirstResponseSeconds: number | null;
};

export type AgentAnalyticsPage = AnalyticsRange & {
  content: AgentAnalytics[];
  page: number;
  size: number;
  totalElements: number;
};

const analyticsPath = (
  orgId: string,
  report: "conversations" | "agents",
  query: Record<string, string | number>,
) =>
  `/api/organizations/${encodeURIComponent(orgId)}/analytics/${report}?${new URLSearchParams(
    Object.entries(query).map(([key, value]) => [key, String(value)]),
  )}`;

/** Conversation volume and response for the organization (admin only). */
export const getConversationAnalytics = (
  accessToken: string,
  orgId: string,
  range: AnalyticsRange,
) =>
  request<ConversationAnalytics>(analyticsPath(orgId, "conversations", range), {
    accessToken,
  });

/** One row per current member, sorted by name (admin only). */
export const getAgentAnalytics = (
  accessToken: string,
  orgId: string,
  range: AnalyticsRange,
  page: number,
  size: number,
) =>
  request<AgentAnalyticsPage>(
    analyticsPath(orgId, "agents", { ...range, page, size }),
    { accessToken },
  );
