import type { Metadata } from "next";
import Link from "next/link";
import OrgCreateForm from "@/components/org-create-form";
import OrgRoleBadge from "@/components/org-role-badge";
import { listOrganizations } from "@/lib/org-api";
import { getAccessToken, getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Organizations · Connecta" };

const DASHBOARD = "/organizations";

// Only a whole number of zero or more counts as a page; anything else shows the first page.
function parsePage(raw: string | undefined): number {
  const page = Number(raw);
  return Number.isInteger(page) && page > 0 ? page : 0;
}

export default async function OrganizationsPage({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  await getCurrentUser(DASHBOARD);
  const accessToken = await getAccessToken(DASHBOARD);
  const page = parsePage((await searchParams).page);
  const result = await listOrganizations(accessToken, page);
  const hasPrevious = page > 0;
  const hasNext = (page + 1) * result.size < result.totalElements;

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-10 p-6 sm:p-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-3xl font-semibold tracking-tight">Organizations</h1>
        <p className="text-zinc-600 dark:text-zinc-400">Teams you belong to.</p>
      </header>

      <section
        aria-labelledby="org-list-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="org-list-heading" className="text-lg font-medium">
          Your organizations
        </h2>
        {result.content.length === 0 ? (
          <p className="text-zinc-500">
            You are not in an organization yet. Create one below or accept an
            invitation.
          </p>
        ) : (
          <ul className="divide-y divide-zinc-200 rounded-md border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
            {result.content.map((organization) => (
              <li
                key={organization.id}
                className="flex flex-wrap items-center gap-x-4 gap-y-1 p-4"
              >
                <Link
                  href={`/organizations/${organization.id}`}
                  className="font-medium underline-offset-4 hover:underline"
                >
                  {organization.name}
                </Link>
                <span className="text-sm text-zinc-500">
                  {organization.slug}
                </span>
                <span className="ml-auto">
                  <OrgRoleBadge role={organization.callerRole} />
                </span>
              </li>
            ))}
          </ul>
        )}
        {(hasPrevious || hasNext) && (
          <nav aria-label="Pagination" className="flex justify-between text-sm">
            {hasPrevious ? (
              <Link
                href={`${DASHBOARD}?page=${page - 1}`}
                className="underline-offset-4 hover:underline"
              >
                Previous
              </Link>
            ) : (
              <span />
            )}
            {hasNext ? (
              <Link
                href={`${DASHBOARD}?page=${page + 1}`}
                className="underline-offset-4 hover:underline"
              >
                Next
              </Link>
            ) : (
              <span />
            )}
          </nav>
        )}
      </section>

      <section
        aria-labelledby="create-org-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="create-org-heading" className="text-lg font-medium">
          Create an organization
        </h2>
        <p className="text-sm text-zinc-500">You become its owner.</p>
        <OrgCreateForm />
      </section>
    </main>
  );
}
