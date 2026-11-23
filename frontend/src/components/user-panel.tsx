import Link from "next/link";
import { getAccessToken, getCurrentUser } from "@/lib/session";
import { listOrganizations } from "@/lib/org-api";
import { roleLabel } from "@/lib/org-permissions";
import { buttonClasses, panelClasses } from "@/components/ui/classes";

const STAT = `${panelClasses} flex flex-col gap-4 p-5`;
const ROW = "flex gap-4 px-5 py-3";
const LABEL = "w-20 shrink-0 text-secondary";

export default async function UserPanel() {
  const user = await getCurrentUser();
  const organizations = await listOrganizations(await getAccessToken(), 0, 1);
  const roles = user.roles.map(roleLabel);

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className={STAT}>
          <span className="text-secondary">Organizations</span>
          <span className="figure text-[2rem] leading-none">
            {organizations.totalElements}
          </span>
          <Link
            href="/organizations"
            className="text-sm text-accent underline-offset-4 hover:underline"
          >
            View organizations
          </Link>
        </div>
        <div className={STAT}>
          <span className="text-secondary">Your role</span>
          <span className="text-[2rem] leading-none">
            {roles.length > 0 ? roles.join(", ") : "None"}
          </span>
          <span className="text-sm text-secondary">Across your account</span>
        </div>
      </div>

      <dl className="divide-y divide-line-subtle overflow-hidden rounded-panel border border-line bg-surface">
        <div className={ROW}>
          <dt className={LABEL}>Name</dt>
          <dd className="font-medium">{user.fullName}</dd>
        </div>
        <div className={ROW}>
          <dt className={LABEL}>Email</dt>
          <dd className="min-w-0 break-all">{user.email}</dd>
        </div>
      </dl>

      <div className="flex flex-wrap items-center gap-4">
        <Link
          href="/organizations"
          className={`${buttonClasses("primary")} min-h-11`}
        >
          Open organizations
        </Link>
        <Link
          href="/account/sessions"
          className="text-sm text-accent underline-offset-4 hover:underline"
        >
          Manage signed-in devices
        </Link>
      </div>
    </div>
  );
}
