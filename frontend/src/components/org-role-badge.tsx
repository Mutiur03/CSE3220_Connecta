import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";

// Admin carries the accent; an agent steps down to neutral.
export const ROLE_BADGE_TONE: Record<OrgRole, string> = {
  ADMIN: "bg-accent/12 text-accent",
  AGENT: "bg-hover text-secondary",
};

export default function OrgRoleBadge({ role }: { role: OrgRole }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-caption font-medium ${ROLE_BADGE_TONE[role]}`}
    >
      {ROLE_LABELS[role]}
    </span>
  );
}
