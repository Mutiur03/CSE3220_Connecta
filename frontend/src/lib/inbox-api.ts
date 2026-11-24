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

export const CONVERSATION_STATUSES = ["OPEN", "CLOSED"] as const;
export type ConversationStatus = (typeof CONVERSATION_STATUSES)[number];

export type Participant = {
  id: string;
  externalId: string;
  displayName: string | null;
};

export type ConversationDetail = {
  id: string;
  organizationId: string;
  channel: InboxChannel;
  status: ConversationStatus;
  participants: Participant[];
  lastMessageAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type Message = {
  id: string;
  direction: "INBOUND" | "OUTBOUND";
  text: string | null;
  /** ISO instant. */
  sentAt: string;
  senderId: string | null;
  senderName: string | null;
};

const conversationPath = (orgId: string, conversationId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/conversations/${encodeURIComponent(conversationId)}`;

export const getConversation = (
  accessToken: string,
  orgId: string,
  conversationId: string,
) =>
  request<ConversationDetail>(conversationPath(orgId, conversationId), {
    accessToken,
  });

/** One page of messages, newest first. */
export const listMessages = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  page: number,
  size: number,
) =>
  request<Page<Message>>(
    `${conversationPath(orgId, conversationId)}/messages?page=${page}&size=${size}`,
    { accessToken },
  );

export const updateConversationStatus = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  status: ConversationStatus,
) =>
  request<ConversationDetail>(conversationPath(orgId, conversationId), {
    method: "PATCH",
    body: { status },
    accessToken,
  });
