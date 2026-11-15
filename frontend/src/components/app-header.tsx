import { cookies } from "next/headers";
import Link from "next/link";
import { Suspense } from "react";
import { ApiError, fetchMe, type User } from "@/lib/api";
import { logout } from "@/lib/auth-actions";
import { ACCESS_COOKIE } from "@/lib/session-cookies";
import { Button } from "@/components/ui/button";

const NAV_LINK =
  "inline-flex min-h-11 items-center rounded-control px-2 text-sm text-ink-muted hover:text-ink";

// Unlike getCurrentUser, this never redirects: the header also renders on /login, where the
// visitor is signed out. A rejected token shows as signed out; the pages still redirect.
// ponytail: the header asks /me once more per page; share it with React cache() if that costs.
export async function signedInUser(): Promise<User | null> {
  const accessToken = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!accessToken) return null;
  try {
    return await fetchMe(accessToken);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

async function HeaderAccount() {
  const user = await signedInUser();
  if (!user) return null;

  return (
    <div className="ml-auto flex items-center gap-2">
      <Link href="/organizations" className={NAV_LINK}>
        Organizations
      </Link>
      <span className="hidden text-sm text-ink-muted sm:inline">
        {user.fullName}
      </span>
      <form action={logout}>
        <Button type="submit" className="min-h-11">
          Sign out
        </Button>
      </form>
    </div>
  );
}

// The brand renders at once; the account part streams in so the header never blocks a page.
export default function AppHeader() {
  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex h-14 w-full max-w-3xl items-center gap-4 px-6 sm:px-8">
        <Link
          href="/"
          className="inline-flex min-h-11 items-center rounded-control font-semibold text-ink"
        >
          Connecta
        </Link>
        <Suspense fallback={null}>
          <HeaderAccount />
        </Suspense>
      </div>
    </header>
  );
}
