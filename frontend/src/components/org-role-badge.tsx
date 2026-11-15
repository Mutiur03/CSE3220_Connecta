import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";

// Owner carries the accent (it is the role that matters most); the others step down in ink.
export const ROLE_BADGE_TONE: Record<OrgRole, string> = {
  OWNER: "border-accent text-accent",
  ADMIN: "border-line-strong text-ink",
  AGENT: "border-line text-ink-muted",
};

export default function OrgRoleBadge({ role }: { role: OrgRole }) {
  return (
    <span
      className={`inline-flex items-center rounded-control border px-2 py-0.5 text-caption font-medium ${ROLE_BADGE_TONE[role]}`}
    >
      {ROLE_LABELS[role]}
    </span>
  );
}
