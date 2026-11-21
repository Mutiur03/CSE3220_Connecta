import type { Metadata } from "next";
import { notFound } from "next/navigation";
import OrgPagination from "@/components/org-pagination";
import { PageHeader } from "@/components/ui/page-header";
import { listAllOrganizations } from "@/lib/org-api";
import { isSystemOwner } from "@/lib/org-permissions";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  parsePageParam,
} from "@/lib/org-pagination";
import { formatDate } from "@/lib/format";
import { getAccessToken, getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "All organizations" };

const PATH = "/admin/organizations";

// The system owner's view: every organization in Connecta, not only the ones they belong to.
export default function AllOrganizationsPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 p-6 sm:p-8">
      <PageHeader
        title="All organizations"
        description="Every organization in Connecta"
      />
      <AllOrganizations searchParams={searchParams} />
    </main>
  );
}

async function AllOrganizations({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const user = await getCurrentUser(PATH);
  // Other roles get the same page as an unknown address, so the route is not advertised.
  if (!isSystemOwner(user.roles)) notFound();

  const accessToken = await getAccessToken(PATH);
  const page = parsePageParam((await searchParams).page);
  const result = await listAllOrganizations(accessToken, page, ORG_PAGE_SIZE);
  const count = pageCount(result.totalElements, result.size);

  return (
    <section aria-label="Organizations" className="flex flex-col gap-3">
      {result.content.length === 0 ? (
        <p className="text-secondary">
          No organizations yet. They appear here as admins create them.
        </p>
      ) : (
        <ul className="divide-y divide-line-subtle overflow-hidden rounded-panel border border-line bg-surface">
          {result.content.map((organization) => (
            <li
              key={organization.id}
              className="flex items-center gap-3 px-4 py-3"
            >
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="truncate font-medium">
                  {organization.name}
                </span>
                <span className="truncate text-sm text-secondary">
                  {organization.slug}
                </span>
              </span>
              <span className="figure text-sm text-secondary">
                {organization.memberCount}{" "}
                {organization.memberCount === 1 ? "member" : "members"}
              </span>
              <time
                dateTime={organization.createdAt}
                className="hidden text-sm text-secondary sm:block"
              >
                {formatDate(organization.createdAt)}
              </time>
            </li>
          ))}
        </ul>
      )}
      <OrgPagination
        label="Organizations pages"
        page={page}
        pageCount={count}
        previousHref={
          page > 0 ? pageHref(PATH, { page }, "page", page - 1) : undefined
        }
        nextHref={
          page + 1 < count
            ? pageHref(PATH, { page }, "page", page + 1)
            : undefined
        }
      />
    </section>
  );
}
