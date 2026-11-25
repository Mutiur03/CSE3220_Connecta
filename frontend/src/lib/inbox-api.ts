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
  /** Media the customer or agent sent. Empty for plain text. */
  attachments: Attachment[];
};

export type AttachmentKind = "IMAGE" | "VIDEO" | "AUDIO" | "FILE";

export type Attachment = {
  id: string;
  kind: AttachmentKind;
  /** Meta media link (signed, expires). */
  url: string;
  fileName: string | null;
  contentType: string | null;
};

/** The path of a file the backend stores itself (an agent's upload, or a copy of a customer's media). */
const OWN_FILE_PATH = /^\/api\/files\/[0-9a-f-]{36}$/;

/**
 * Our own files are stored with the public address Meta downloads them from (a tunnel in development), which
 * can answer a browser with a warning page. The browser loads them from the API address this app already uses;
 * Meta's own links are left as they are.
 */
export function browserFileUrl(url: string): string {
  try {
    const { pathname } = new URL(url);
    if (!OWN_FILE_PATH.test(pathname)) return url;
    const base = (
      process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
    ).replace(/\/+$/, "");
    return `${base}${pathname}`;
  } catch {
    return url;
  }
}

/** An older backend sends no `attachments`; read that as none. */
const withAttachments = (message: Message): Message => ({
  ...message,
  attachments: (message.attachments ?? []).map((attachment) => ({
    ...attachment,
    url: browserFileUrl(attachment.url),
  })),
});

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
  ).then((result) => ({
    ...result,
    content: result.content.map(withAttachments),
  }));

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

/** Sends one file (multipart `file`) through the conversation's channel, as its own message. */
export const sendAttachment = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  file: File,
) => {
  const form = new FormData();
  form.append("file", file);
  return request<Message>(
    `${conversationPath(orgId, conversationId)}/messages/attachments`,
    { method: "POST", form, accessToken },
  ).then(withAttachments);
};

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
