import { cookies } from "next/headers";
import { Suspense } from "react";
import { ApiError, fetchMe, type User } from "@/lib/api";
import { logout } from "@/lib/auth-actions";
import { ACCESS_COOKIE } from "@/lib/session-cookies";
import { HomeIcon, LogOutIcon, UsersIcon } from "./icons";
import { RAIL_CONTROL } from "./rail-button";
import RailLink from "./rail-link";
import ThemeToggle from "./theme-toggle";

// Unlike getCurrentUser, this never redirects: the rail also renders on /login, where the
// visitor is signed out. A rejected token shows as signed out; the pages still redirect.
// ponytail: the rail asks /me once more per page; share it with React cache() if that costs.
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

async function RailAccount() {
  const user = await signedInUser();
  if (!user) return null;

  return (
    <>
      <RailLink href="/" label="Home" exact>
        <HomeIcon />
      </RailLink>
      <RailLink href="/organizations" label="Organizations">
        <UsersIcon />
      </RailLink>
      <form action={logout} className="order-last">
        <button
          type="submit"
          title={`Sign out ${user.fullName}`}
          className={RAIL_CONTROL}
        >
          <LogOutIcon />
          <span className="sr-only">Sign out</span>
        </button>
      </form>
    </>
  );
}

// A 56px icon rail on the left; under 800px it becomes a bar along the bottom. The frame
// renders at once and the account part streams in, so the layout never shifts.
export default function AppRail() {
  return (
    <nav
      aria-label="Main"
      className="order-last flex h-14 shrink-0 items-center justify-around border-t border-line bg-surface px-2 max-[799px]:sticky max-[799px]:bottom-0 min-[800px]:sticky min-[800px]:top-0 min-[800px]:order-first min-[800px]:h-dvh min-[800px]:w-14 min-[800px]:flex-col min-[800px]:justify-start min-[800px]:gap-1 min-[800px]:self-start min-[800px]:border-r min-[800px]:border-t-0 min-[800px]:px-0 min-[800px]:py-3"
    >
      <span
        aria-hidden="true"
        className="mb-2 hidden size-8 items-center justify-center rounded-control bg-accent text-sm font-semibold text-accent-ink min-[800px]:inline-flex"
      >
        C
      </span>
      <Suspense fallback={null}>
        <RailAccount />
      </Suspense>
      <div className="min-[800px]:mt-auto">
        <ThemeToggle />
      </div>
    </nav>
  );
}
