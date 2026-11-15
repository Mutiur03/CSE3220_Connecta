import Link from "next/link";
import { logout } from "@/lib/auth-actions";
import { getCurrentUser } from "@/lib/session";

// Reads the session cookie, so it renders per request; render it inside <Suspense>.
export default async function UserPanel() {
  const user = await getCurrentUser();

  return (
    <div className="flex flex-col items-center gap-3">
      <p className="text-lg">
        Signed in as <span className="font-medium">{user.fullName}</span>
      </p>
      <p className="text-sm text-zinc-500">
        {user.email} · {user.roles.join(", ")}
      </p>
      <Link
        href="/organizations"
        className="rounded-md border border-zinc-300 px-4 py-2 text-sm font-medium hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
      >
        Organizations
      </Link>
      <form action={logout}>
        <button
          type="submit"
          className="rounded-md border border-zinc-300 px-4 py-2 text-sm font-medium hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-800"
        >
          Sign out
        </button>
      </form>
    </div>
  );
}
