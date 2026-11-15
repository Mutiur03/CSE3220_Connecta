import Link from "next/link";
import { logout } from "@/lib/auth-actions";
import { getCurrentUser } from "@/lib/session";
import { Button } from "@/components/ui/button";
import { buttonClasses } from "@/components/ui/classes";

// Reads the session cookie, so it renders per request; render it inside <Suspense>.
export default async function UserPanel() {
  const user = await getCurrentUser();

  return (
    <div className="flex flex-col items-center gap-3">
      <p className="text-lg">
        Signed in as <span className="font-medium">{user.fullName}</span>
      </p>
      <p className="text-sm text-ink-muted">
        {user.email} · {user.roles.join(", ")}
      </p>
      <Link href="/organizations" className={buttonClasses("secondary")}>
        Organizations
      </Link>
      <form action={logout}>
        <Button type="submit">Sign out</Button>
      </form>
    </div>
  );
}
