import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import OrgCreateForm from "@/components/org-create-form";
import OrgPagination from "@/components/org-pagination";
import OrgRoleBadge from "@/components/org-role-badge";
import { OrgSectionSkeleton } from "@/components/org-skeleton";
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
      <header className="flex flex-col gap-1">
        <h1 className="text-display font-semibold tracking-tight">
          Organizations
        </h1>
        <p className="text-secondary">Teams you belong to.</p>
      </header>

      <Suspense fallback={<OrgSectionSkeleton label="your organizations" />}>
        <OrganizationList searchParams={searchParams} />
      </Suspense>

      <section
        aria-labelledby="create-org-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="create-org-heading" className="text-lg font-semibold">
          Create an organization
        </h2>
        <p className="text-sm text-secondary">You become its owner.</p>
        <OrgCreateForm />
      </section>
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
      <h2 id="org-list-heading" className="text-lg font-semibold">
        Your organizations
      </h2>
      {result.content.length === 0 ? (
        <p className="text-secondary">
          You are not in an organization yet. Create one below or{" "}
          <Link
            href="/invitations/accept"
            className="text-accent underline-offset-4 hover:underline"
          >
            accept an invitation
          </Link>
          .
        </p>
      ) : (
        <ul className="divide-y divide-line-subtle border-y border-line">
          {result.content.map((organization) => (
            <li
              key={organization.id}
              className="relative flex flex-wrap items-center gap-x-4 gap-y-1 py-3 hover:bg-hover"
            >
              <Link
                href={`/organizations/${organization.id}`}
                className="font-medium after:absolute after:inset-0"
              >
                {organization.name}
              </Link>
              <span className="text-sm text-secondary">
                {organization.slug}
              </span>
              <span className="ml-auto">
                <OrgRoleBadge role={organization.callerRole} />
              </span>
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
