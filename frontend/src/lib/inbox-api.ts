import { request } from "./api";
import type { Page } from "./org-api";
import {
  inboxListQuery,
  type InboxChannel,
  type InboxFilters,
} from "./inbox-view";

export type Conversation = {
  id: string;
  channel: InboxChannel;
  participantName: string | null;
  lastMessagePreview: string | null;
  /** ISO instant of the newest message. */
  lastMessageAt: string | null;
};

export const listConversations = (
  accessToken: string,
  orgId: string,
  filters: InboxFilters,
  page: number,
  size: number,
) =>
  request<Page<Conversation>>(
    `/api/organizations/${encodeURIComponent(orgId)}/conversations?${inboxListQuery(filters, page, size)}`,
    { accessToken },
  );
