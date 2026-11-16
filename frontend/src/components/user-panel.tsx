import Link from "next/link";
import { getCurrentUser } from "@/lib/session";
import { ROLE_LABELS, isOrgRole } from "@/lib/org-permissions";
import { buttonClasses } from "@/components/ui/classes";

const ROW = "flex gap-4 py-2.5";
const LABEL = "w-20 shrink-0 text-secondary";

// Reads the session cookie, so it renders per request; render it inside <Suspense>.
export default async function UserPanel() {
  const user = await getCurrentUser();
  const roles = user.roles.map((role) =>
    isOrgRole(role) ? ROLE_LABELS[role] : role,
  );

  return (
    <div className="flex flex-col gap-6">
      <dl className="divide-y divide-line-subtle border-y border-line">
        <div className={ROW}>
          <dt className={LABEL}>Name</dt>
          <dd className="font-medium">{user.fullName}</dd>
        </div>
        <div className={ROW}>
          <dt className={LABEL}>Email</dt>
          <dd className="min-w-0 break-all">{user.email}</dd>
        </div>
        <div className={ROW}>
          <dt className={LABEL}>Role</dt>
          <dd>{roles.length > 0 ? roles.join(", ") : "None"}</dd>
        </div>
      </dl>
      <div>
        <Link
          href="/organizations"
          className={`${buttonClasses("primary")} min-h-11`}
        >
          Open organizations
        </Link>
      </div>
    </div>
  );
}

// Same rows as the panel, so the content does not jump when it arrives.
export function UserPanelSkeleton() {
  return (
    <div role="status" className="flex flex-col">
      <span className="sr-only">Loading your account</span>
      <div aria-hidden="true" className="flex flex-col gap-6">
        <div className="divide-y divide-line-subtle border-y border-line">
          {[0, 1, 2].map((row) => (
            <div key={row} className={`${ROW} h-[2.75rem] items-center`}>
              <div className="h-3 w-16 rounded-control bg-hover" />
              <div className="h-3 w-40 rounded-control bg-hover" />
            </div>
          ))}
        </div>
        <div className="h-8 w-40 rounded-control bg-hover pointer-coarse:h-11" />
      </div>
    </div>
  );
}
