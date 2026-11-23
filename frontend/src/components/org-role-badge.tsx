import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { Badge } from "./ui/badge";
import type { BadgeTone } from "./ui/classes";

// Admin carries the accent; an agent steps down to neutral.
export const ROLE_BADGE_TONE: Record<OrgRole, BadgeTone> = {
  ADMIN: "accent",
  AGENT: "neutral",
};

export default function OrgRoleBadge({ role }: { role: OrgRole }) {
  return <Badge tone={ROLE_BADGE_TONE[role]}>{ROLE_LABELS[role]}</Badge>;
}
