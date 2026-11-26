import type { Metadata } from "next";
import Link from "next/link";
import OrgPagination from "@/components/org-pagination";
import TeamWorkloadTable from "@/components/team-workload-table";
import { Alert } from "@/components/ui/alert";
import { listAvailability } from "@/lib/assignment-api";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  parsePageParam,
} from "@/lib/org-pagination";
import { loadOrganization, loadSession, notFoundOnMissing } from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Team workload" };

// Everyone's availability and load is admin-only on the backend, so an agent gets a notice and the page makes
// no workload request.
export default async function TeamPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  const header = (
    <header className="flex flex-col gap-1">
      <Link
        href={`/organizations/${id}`}
        className="self-start text-sm text-secondary underline-offset-4 hover:underline"
      >
        {organization.name}
      </Link>
      <h1 className="text-display font-medium">Team workload</h1>
      <p className="text-secondary">
        Who is taking new conversations and how many open ones each member
        holds. Members switch their own status from the inbox.
      </p>
    </header>
  );
  if (organization.callerRole !== "ADMIN") {
    return (
      <>
        {header}
        <Alert tone="info">
          Only an admin of this organization sees the team workload.
        </Alert>
      </>
    );
  }

  const page = parsePageParam((await searchParams).page);
  const { accessToken } = await loadSession(id);
  const members = await notFoundOnMissing(
    listAvailability(accessToken, id, page, ORG_PAGE_SIZE),
  );
  const count = pageCount(members.totalElements, members.size);
  const link = (target: number) =>
    pageHref(`/organizations/${id}/team`, {}, "page", target);

  return (
    <>
      {header}
      <TeamWorkloadTable members={members.content} now={new Date()} />
      <OrgPagination
        label="Team pages"
        page={page}
        pageCount={count}
        previousHref={page > 0 ? link(page - 1) : undefined}
        nextHref={page + 1 < count ? link(page + 1) : undefined}
      />
    </>
  );
}
