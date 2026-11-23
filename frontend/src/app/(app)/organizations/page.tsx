import type { Metadata } from "next";
import Link from "next/link";
import { BuildingIcon, ChevronRightIcon } from "@/components/icons";
import { buttonClasses } from "@/components/ui/classes";
import { PageHeader } from "@/components/ui/page-header";
import OrgPagination from "@/components/org-pagination";
import OrgRoleBadge from "@/components/org-role-badge";
import { listOrganizations } from "@/lib/org-api";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  parsePageParam,
} from "@/lib/org-pagination";
import { getAccessToken, getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Organizations" };

const DASHBOARD = "/organizations";

export default function OrganizationsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  return (
    <>
      <PageHeader title="Organizations" description="Teams you belong to" />

      <OrganizationList searchParams={searchParams} />
    </>
  );
}

async function OrganizationList({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  await getCurrentUser(DASHBOARD);
  const accessToken = await getAccessToken(DASHBOARD);
  const page = parsePageParam((await searchParams).page);
  const result = await listOrganizations(accessToken, page, ORG_PAGE_SIZE);
  const count = pageCount(result.totalElements, result.size);

  return (
    <section aria-labelledby="org-list-heading" className="flex flex-col gap-3">
      <h2 id="org-list-heading" className="text-lg font-medium">
        Your organizations
      </h2>
      {result.content.length === 0 ? (
        <div className="flex flex-wrap items-start gap-4 rounded-panel border border-line bg-surface p-4 sm:p-5">
          <span className="grid size-10 shrink-0 place-items-center rounded-control border border-line bg-app text-primary">
            <BuildingIcon />
          </span>
          <div className="flex min-w-0 flex-1 basis-56 flex-col gap-1">
            <p className="text-lg font-medium">No organizations yet</p>
            <p className="text-sm text-secondary">
              Create one from the organization menu in the sidebar, or open the
              invitation link a teammate sent you.
            </p>
          </div>
          <div className="shrink-0 pl-14 sm:ml-auto sm:pl-0">
            <Link
              href="/invitations/accept"
              className={buttonClasses("secondary")}
            >
              Accept an invitation
            </Link>
          </div>
        </div>
      ) : (
        <ul className="divide-y divide-line-subtle overflow-hidden rounded-panel border border-line bg-surface">
          {result.content.map((organization) => (
            <li
              key={organization.id}
              className="group relative flex items-center gap-3 px-2 py-3 transition-colors duration-150 ease-out hover:bg-hover motion-reduce:transition-none"
            >
              <span
                aria-hidden="true"
                className="inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-hover text-caption font-medium text-secondary transition-colors duration-150 group-hover:bg-surface"
              >
                {organization.name.slice(0, 2).toUpperCase()}
              </span>
              <span className="flex min-w-0 flex-1 flex-col">
                <Link
                  href={`/organizations/${organization.id}`}
                  className="truncate font-medium after:absolute after:inset-0"
                >
                  {organization.name}
                </Link>
                <span className="truncate text-sm text-secondary">
                  {organization.slug}
                </span>
              </span>
              <OrgRoleBadge role={organization.callerRole} />
              <ChevronRightIcon />
            </li>
          ))}
        </ul>
      )}
      <OrgPagination
        label="Organizations pages"
        page={page}
        pageCount={count}
        previousHref={
          page > 0 ? pageHref(DASHBOARD, { page }, "page", page - 1) : undefined
        }
        nextHref={
          page + 1 < count
            ? pageHref(DASHBOARD, { page }, "page", page + 1)
            : undefined
        }
      />
    </section>
  );
}
