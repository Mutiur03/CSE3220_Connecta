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
