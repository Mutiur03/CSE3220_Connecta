import { cookies } from "next/headers";
import { ApiError, type User } from "@/lib/api";
import { listMemberOrganizations } from "@/lib/org-api";
import { logout } from "@/lib/auth-actions";
import { isSystemOwner } from "@/lib/org-permissions";
import { fetchMeOnce } from "@/lib/session";
import { ACCESS_COOKIE } from "@/lib/session-cookies";
import {
  BuildingIcon,
  HomeIcon,
  LogOutIcon,
  SettingsIcon,
  UsersIcon,
} from "./icons";
import { RAIL_CONTROL, RAIL_LABEL } from "./rail-button";
import RailShell from "./rail-shell";
import { CURRENT_ORG_COOKIE } from "@/lib/current-org";
import OrgSwitcher from "./org-switcher";
import RailLink from "./rail-link";
import RailOrgSection from "./rail-org-section";
import ThemeToggle from "./theme-toggle";

// Unlike getCurrentUser, this never redirects: the rail also renders on /login, where the
// visitor is signed out. A rejected token shows as signed out; the pages still redirect.
export async function signedInUser(): Promise<User | null> {
  const accessToken = (await cookies()).get(ACCESS_COOKIE)?.value;
  if (!accessToken) return null;
  try {
    return await fetchMeOnce(accessToken);
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
    const page = await listMemberOrganizations(accessToken);
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
      <div className="flex flex-col gap-0.5">
        <RailLink href="/" label="Home" exact>
          <HomeIcon />
        </RailLink>
        <RailLink href="/organizations" label="Organizations" exact>
          <UsersIcon />
        </RailLink>
        {isSystemOwner(user.roles) && (
          <RailLink href="/admin/organizations" label="All organizations">
            <BuildingIcon />
          </RailLink>
        )}
        {isSystemOwner(user.roles) && (
          <RailLink href="/admin/users" label="Users">
            <UsersIcon />
          </RailLink>
        )}
      </div>
      <RailOrgSection
        organizations={organizations.map(({ id, name, callerRole }) => ({
          id,
          name,
          callerRole,
        }))}
        remembered={remembered}
      />
      <div className="mt-auto flex flex-col gap-0.5">
        <RailLink href="/settings" label="Settings">
          <SettingsIcon />
        </RailLink>
        <ThemeToggle />
        <form action={logout}>
          <button type="submit" className={RAIL_CONTROL}>
            <LogOutIcon />
            <span className={RAIL_LABEL}>Sign out</span>
          </button>
        </form>
        <div className="mt-2 flex items-center gap-2.5 border-t border-line-subtle px-2.5 pt-3">
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

// A 240px sidebar on the left; under 800px a top bar opens it as a drawer (see RailShell).
export default function AppRail() {
  return (
    <RailShell>
      <RailAccount />
    </RailShell>
  );
}
