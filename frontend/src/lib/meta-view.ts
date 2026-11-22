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
