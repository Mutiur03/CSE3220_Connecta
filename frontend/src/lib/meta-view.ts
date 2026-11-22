import type { OrgRole } from "./org-permissions";

/** Connecting Meta is an admin's job. The backend enforces it; the UI only hides the controls. */
export const canConnectMeta = (caller: OrgRole) => caller === "ADMIN";

/** The Page ids the form sent, trimmed, with blanks and repeats removed, in the order chosen. */
export function parsePageIds(values: FormDataEntryValue[]): string[] {
  const ids = values.map((value) => String(value).trim()).filter(Boolean);
  return [...new Set(ids)];
}

/** One plain line for the Facebook connection's status. */
export function facebookStatusLine(
  connected: boolean,
  connectedAt: string | null,
  formatDate: (iso: string) => string,
): string {
  if (!connected || !connectedAt) return "Not connected";
  return `Connected on ${formatDate(connectedAt)}`;
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
