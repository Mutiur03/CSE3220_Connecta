export type InvitationBadgeTone = "neutral" | "accent" | "success";

const TONE_BY_STATUS: Record<string, InvitationBadgeTone> = {
  Pending: "accent",
  Accepted: "success",
  Revoked: "neutral",
  Expired: "neutral",
};

/** Badge colour for an invitation status label from invitationStatusLabel. */
export function invitationBadgeTone(statusLabel: string): InvitationBadgeTone {
  return TONE_BY_STATUS[statusLabel] ?? "neutral";
}
