import { request } from "./api";
import type { Page } from "./org-api";
import {
  apiStatus,
  inboxListQuery,
  type InboxChannel,
  type InboxFilters,
} from "./inbox-view";

export type Conversation = {
  id: string;
  channel: InboxChannel;
  status: ConversationStatus;
  /** A customer wrote since a member last read it. */
  unread: boolean;
  participantName: string | null;
  /** Meta picture link (https, signed, may expire) or null. */
  avatarUrl: string | null;
  lastMessagePreview: string | null;
  /** ISO instant of the newest message. */
  lastMessageAt: string | null;
};

/** One page of the list. The tab becomes the API's status filter (Open by default, none for All). */
export function listConversations(
  accessToken: string,
  orgId: string,
  filters: InboxFilters,
  page: number,
  size: number,
) {
  const query = new URLSearchParams(
    inboxListQuery({ ...filters, tab: undefined }, page, size),
  );
  const status = apiStatus(filters.tab);
  if (status) query.set("status", status);
  return request<Page<Conversation>>(
    `/api/organizations/${encodeURIComponent(orgId)}/conversations?${query}`,
    { accessToken },
  );
}

export const CONVERSATION_STATUSES = ["OPEN", "CLOSED"] as const;
export type ConversationStatus = (typeof CONVERSATION_STATUSES)[number];

export type Participant = {
  id: string;
  externalId: string;
  displayName: string | null;
  /** Meta picture link (https, signed, may expire) or null. */
  avatarUrl: string | null;
};

export type ConversationDetail = {
  id: string;
  organizationId: string;
  channel: InboxChannel;
  status: ConversationStatus;
  unread: boolean;
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

export const MESSAGE_MAX_LENGTH = 1000;

/** Sends an agent reply through the conversation's channel. */
export const sendMessage = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  text: string,
) =>
  request<Message>(`${conversationPath(orgId, conversationId)}/messages`, {
    method: "POST",
    body: { text },
    accessToken,
  });

/** Marks the conversation read (`unread: false`) or unread for the whole team. */
export const updateReadState = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  unread: boolean,
) =>
  request<void>(`${conversationPath(orgId, conversationId)}/read-state`, {
    method: "PUT",
    body: { unread },
    accessToken,
  });
