import { cookies } from "next/headers";
import Link from "next/link";
import { ApiError, fetchMe, type User } from "@/lib/api";
import { listOrganizations, MAX_PAGE_SIZE } from "@/lib/org-api";
import { logout } from "@/lib/auth-actions";
import { isSystemOwner } from "@/lib/org-permissions";
import { ACCESS_COOKIE } from "@/lib/session-cookies";
import { BuildingIcon, HomeIcon, LogOutIcon, UsersIcon } from "./icons";
import { RAIL_CONTROL, RAIL_LABEL } from "./rail-button";
import BrandMark from "./brand-mark";
import OrgSwitcher, { CURRENT_ORG_COOKIE } from "./org-switcher";
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

const initials = (name: string) =>
  name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");

// The sidebar must never break a page, so a failed list reads as "no organizations".
async function memberOrganizations() {
  const store = await cookies();
  const accessToken = store.get(ACCESS_COOKIE)?.value;
  if (!accessToken) return { organizations: [], remembered: undefined };
  try {
    const page = await listOrganizations(accessToken, 0, MAX_PAGE_SIZE);
    return {
      organizations: page.content,
      remembered: store.get(CURRENT_ORG_COOKIE)?.value,
    };
  } catch {
    return { organizations: [], remembered: undefined };
  }
}

async function RailAccount() {
  const user = await signedInUser();
  if (!user) return null;
  const { organizations, remembered } = await memberOrganizations();

  return (
    <>
      <OrgSwitcher organizations={organizations} remembered={remembered} />
      <div className="flex flex-col gap-0.5 max-[799px]:contents">
        <RailLink href="/" label="Home" exact>
          <HomeIcon />
        </RailLink>
        <RailLink href="/organizations" label="Organizations">
          <UsersIcon />
        </RailLink>
        {isSystemOwner(user.roles) && (
          <RailLink href="/admin/organizations" label="All organizations">
            <BuildingIcon />
          </RailLink>
        )}
      </div>
      <div className="mt-auto flex flex-col gap-0.5 max-[799px]:contents">
        <ThemeToggle />
        <form action={logout} className="max-[799px]:order-last">
          <button type="submit" className={RAIL_CONTROL}>
            <LogOutIcon />
            <span className={RAIL_LABEL}>Sign out</span>
          </button>
        </form>
        <div className="mt-2 flex items-center gap-2.5 border-t border-line-subtle px-2.5 pt-3 max-[799px]:hidden">
          <span
            aria-hidden="true"
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-hover text-caption font-medium text-secondary"
          >
            {initials(user.fullName)}
          </span>
          <span className="flex min-w-0 flex-col">
            <span className="truncate text-sm font-medium">
              {user.fullName}
            </span>
            <span className="truncate text-caption text-secondary">
              {user.email}
            </span>
          </span>
        </div>
      </div>
    </>
  );
}

// A 240px sidebar on the left; under 800px it becomes a bar of icons along the bottom. The frame
// renders at once and the account part streams in, so the layout never shifts.
export default function AppRail() {
  return (
    <nav
      aria-label="Main"
      className="order-last flex h-14 shrink-0 items-center justify-around border-t border-line bg-surface px-2 max-[799px]:sticky max-[799px]:bottom-0 min-[800px]:sticky min-[800px]:top-0 min-[800px]:order-first min-[800px]:h-dvh min-[800px]:w-60 min-[800px]:flex-col min-[800px]:items-stretch min-[800px]:justify-start min-[800px]:gap-6 min-[800px]:self-start min-[800px]:border-r min-[800px]:border-t-0 min-[800px]:px-3 min-[800px]:py-4"
    >
      <Link
        href="/"
        className="hidden items-center gap-2.5 rounded-control px-2.5 min-[800px]:flex"
      >
        <BrandMark className="size-9" />
        <span className="text-lg font-medium">Connecta</span>
      </Link>
      <RailAccount />
    </nav>
  );
}
