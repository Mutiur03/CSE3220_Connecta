import Link from "next/link";
import { getCurrentUser } from "@/lib/session";
import { ROLE_LABELS, isOrgRole } from "@/lib/org-permissions";
import { Card } from "@/components/ui/card";
import { buttonClasses, cardClasses } from "@/components/ui/classes";

// Reads the session cookie, so it renders per request; render it inside <Suspense>.
export default async function UserPanel() {
  const user = await getCurrentUser();
  const roles = user.roles.map((role) =>
    isOrgRole(role) ? ROLE_LABELS[role] : role,
  );

  return (
    <Card className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="text-title font-semibold tracking-tight">
          {user.fullName}
        </p>
        <p className="text-sm text-ink-muted">{user.email}</p>
      </div>
      <dl className="grid grid-cols-[auto_1fr] items-baseline gap-x-6 gap-y-2 text-sm">
        <dt className="text-ink-muted">Role</dt>
        <dd>{roles.length > 0 ? roles.join(", ") : "None"}</dd>
      </dl>
      <div>
        <Link
          href="/organizations"
          className={`${buttonClasses("primary")} min-h-11`}
        >
          Open organizations
        </Link>
      </div>
    </Card>
  );
}

// Same height as the panel, so the content does not jump when it arrives.
export function UserPanelSkeleton() {
  return (
    <div role="status" className="flex flex-col">
      <span className="sr-only">Loading your account</span>
      <div
        aria-hidden="true"
        className={`${cardClasses} flex h-[196px] flex-col gap-5`}
      >
        <div className="flex flex-col gap-2">
          <div className="h-5 w-40 rounded-control bg-hover" />
          <div className="h-4 w-56 rounded-control bg-hover" />
        </div>
        <div className="h-4 w-32 rounded-control bg-hover" />
        <div className="h-11 w-44 rounded-control bg-hover" />
      </div>
    </div>
  );
}
