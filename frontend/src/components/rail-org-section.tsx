"use client";

import { usePathname } from "next/navigation";
import { selectCurrentOrganization } from "@/lib/current-org";
import { ArchiveIcon, PackageIcon, TagIcon } from "./icons";
import RailLink from "./rail-link";

type RailOrganization = { id: string; name: string };

/**
 * The selected organization's section in the sidebar, on every signed-in page. The selection is
 * the same one the organization switcher shows (see selectCurrentOrganization), so the two agree.
 */
export default function RailOrgSection({
  organizations,
  remembered,
}: {
  organizations: RailOrganization[];
  remembered?: string;
}) {
  const current = selectCurrentOrganization(organizations, usePathname(), remembered);
  if (!current) return null;

  return (
    <div role="group" aria-label={current.name} className="flex flex-col gap-0.5 max-[799px]:contents">
      <p className="truncate px-2.5 pt-2 text-caption text-secondary max-[799px]:sr-only">
        {current.name}
      </p>
      <RailLink href={`/organizations/${current.id}/products`} label="Products">
        <PackageIcon />
      </RailLink>
      <RailLink href={`/organizations/${current.id}/categories`} label="Categories">
        <TagIcon />
      </RailLink>
      <RailLink href={`/organizations/${current.id}/inventory`} label="Inventory">
        <ArchiveIcon />
      </RailLink>
    </div>
  );
}
