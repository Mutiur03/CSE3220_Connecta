export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en", { dateStyle: "medium" });
}

/** A PENDING invitation whose expiry has passed can no longer be accepted, so it reads as expired. */
export function invitationStatusLabel(
  status: "PENDING" | "ACCEPTED" | "REVOKED",
  expiresAt: string,
  now: Date,
): string {
  if (status === "ACCEPTED") return "Accepted";
  if (status === "REVOKED") return "Revoked";
  return new Date(expiresAt) > now ? "Pending" : "Expired";
}

/** A unit price with two decimals and no locale grouping, so "12.50" reads the same everywhere. */
export function formatPrice(value: number): string {
  return value.toFixed(2);
}

/** A movement's time for a history table: date and clock time in the reader's locale. */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/** Clock time only, for a message whose day is already shown above it. */
export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en", { timeStyle: "short" });
}

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
const RELATIVE_LIMIT_DAYS = 7;
const relative = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/**
 * "5 minutes ago", "yesterday", then a date after a week. Relative wording needs no time zone, so it can be
 * built on the server and read the same everywhere.
 */
export function formatRelative(iso: string, now: Date): string {
  const elapsed = Math.max(0, now.getTime() - new Date(iso).getTime());
  if (elapsed < MINUTE_MS) return "just now";
  if (elapsed < HOUR_MS) {
    return relative.format(-Math.floor(elapsed / MINUTE_MS), "minute");
  }
  if (elapsed < DAY_MS) {
    return relative.format(-Math.floor(elapsed / HOUR_MS), "hour");
  }
  if (elapsed < RELATIVE_LIMIT_DAYS * DAY_MS) {
    return relative.format(-Math.floor(elapsed / DAY_MS), "day");
  }
  return formatDate(iso);
}
