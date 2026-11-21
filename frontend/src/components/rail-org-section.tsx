"use client";

import { usePathname } from "next/navigation";
import { PackageIcon } from "./icons";
import RailLink from "./rail-link";

/** The organization id in an /organizations/<uuid>/... path, or undefined outside one. */
export function orgIdFromPath(pathname: string): string | undefined {
  return /^\/organizations\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\/|$)/i.exec(
    pathname,
  )?.[1];
}

type RailOrganization = { id: string; name: string };

/**
 * The current organization's section in the sidebar. It shows only inside an organization route;
 * the organization's own links live here, so they follow the URL.
 */
export default function RailOrgSection({
  organizations,
}: {
  organizations: RailOrganization[];
}) {
  const orgId = orgIdFromPath(usePathname());
  if (!orgId) return null;
  const name = organizations.find((org) => org.id === orgId)?.name ?? "Organization";

  return (
    <div role="group" aria-label={name} className="flex flex-col gap-0.5 max-[799px]:contents">
      <p className="truncate px-2.5 pt-2 text-caption text-secondary max-[799px]:sr-only">
        {name}
      </p>
      <RailLink href={`/organizations/${orgId}/products`} label="Products">
        <PackageIcon />
      </RailLink>
    </div>
  );
}
