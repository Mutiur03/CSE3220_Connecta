import { formatRelative } from "./format";
import type { HistoryImportError } from "./meta-api";
import type { OrgRole } from "./org-permissions";

/** Connecting Meta is an admin's job. The backend enforces it; the UI only hides the controls. */
export const canConnectMeta = (caller: OrgRole) => caller === "ADMIN";

/** The Page ids the form sent, trimmed, with blanks and repeats removed, in the order chosen. */
export function parsePageIds(values: FormDataEntryValue[]): string[] {
  const ids = values.map((value) => String(value).trim()).filter(Boolean);
  return [...new Set(ids)];
}

/** Where a channel's connection stands. An expired Facebook grant still lists Pages but stops delivery. */
export type ChannelStatus = "connected" | "expired" | "not_connected";

type FacebookGrant = {
  connected: boolean;
  connectedAt: string | null;
  expiresAt: string | null;
};

export function facebookStatus(grant: FacebookGrant, now: Date): ChannelStatus {
  if (!grant.connected || !grant.connectedAt) return "not_connected";
  if (grant.expiresAt && new Date(grant.expiresAt) <= now) return "expired";
  return "connected";
}

/** One plain line under the Messenger row: what is connected, and until when. */
export function facebookDetailLine(
  grant: FacebookGrant,
  now: Date,
  formatDate: (iso: string) => string,
): string {
  const status = facebookStatus(grant, now);
  if (status === "not_connected" || !grant.connectedAt) {
    return "Sign in with Facebook to choose which Pages send messages here.";
  }
  if (status === "expired" && grant.expiresAt) {
    return `Facebook access ended on ${formatDate(grant.expiresAt)}. Reconnect to keep receiving messages.`;
  }
  const since = `Connected on ${formatDate(grant.connectedAt)}.`;
  return grant.expiresAt
    ? `${since} Access lasts until ${formatDate(grant.expiresAt)}.`
    : since;
}

export const STATUS_LABELS: Record<ChannelStatus, string> = {
  connected: "Connected",
  expired: "Access expired",
  not_connected: "Not connected",
};

const RETRY_NOTE =
  "It retries when the server restarts or the account is reconnected.";

const HISTORY_IMPORT_TEXT: Record<HistoryImportError, string> = {
  PERMISSION: `Importing past conversations failed: permission missing. ${RETRY_NOTE}`,
  FETCH: `Importing past conversations failed: Meta could not be read. ${RETRY_NOTE}`,
};

/** The quiet warning for an account whose last history import failed, or null when it did not. */
export function historyImportNotice(
  error: HistoryImportError | null | undefined,
): string | null {
  return error ? HISTORY_IMPORT_TEXT[error] : null;
}

/** When a webhook last reached a connection, in words; a connection nothing has reached says so. */
export function webhookLine(lastWebhookAt: string | null, now: Date): string {
  return lastWebhookAt
    ? `Last webhook ${formatRelative(lastWebhookAt, now)}`
    : "No webhook received yet";
}

/** The query parameters the Meta callbacks send the browser back with. */
export type ConnectionResultParams = {
  facebook?: string;
  instagram?: string;
  metaError?: string;
};

export type ConnectionFlash = { tone: "success" | "danger"; text: string };

const ERROR_TEXT: Record<string, string> = {
  denied:
    "The Facebook login was cancelled. Connect again to choose your Pages.",
  invalid_state:
    "That connection link has expired. Start the connection again.",
  forbidden: "Only an admin of this organization can connect Meta accounts.",
  not_configured: "Meta is not set up on this server yet.",
  meta_failed: "Meta could not finish the connection. Try again.",
};

/** The one message to show after a Meta callback, or null when the page was opened normally. */
export function connectionFlash(
  params: ConnectionResultParams,
): ConnectionFlash | null {
  if (params.facebook === "connected") {
    return {
      tone: "success",
      text: "Facebook connected. Choose the Pages to receive messages from below.",
    };
  }
  if (params.instagram === "connected") {
    return { tone: "success", text: "Instagram account connected." };
  }
  const text = params.metaError ? ERROR_TEXT[params.metaError] : undefined;
  return text ? { tone: "danger", text } : null;
}
