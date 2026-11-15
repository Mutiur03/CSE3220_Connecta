import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";

const TONE: Record<OrgRole, string> = {
  OWNER: "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900",
  ADMIN:
    "border border-zinc-900 text-zinc-900 dark:border-zinc-100 dark:text-zinc-100",
  AGENT: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
};

export default function OrgRoleBadge({ role }: { role: OrgRole }) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${TONE[role]}`}
    >
      {ROLE_LABELS[role]}
    </span>
  );
}
