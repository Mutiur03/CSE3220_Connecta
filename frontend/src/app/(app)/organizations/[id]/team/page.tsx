import type { Metadata } from "next";
import Link from "next/link";
import OrgPagination from "@/components/org-pagination";
import AvailabilityRangeForm from "@/components/availability-range-form";
import AvailabilityTimeTable from "@/components/availability-time-table";
import TeamWorkloadTable from "@/components/team-workload-table";
import { Alert } from "@/components/ui/alert";
import { listAvailability } from "@/lib/assignment-api";
import { getAvailabilityReport } from "@/lib/availability-report-api";
import { isoDate, parseRange } from "@/lib/availability-report-view";
import { section } from "@/lib/inventory-view";
import { MAX_PAGE_SIZE } from "@/lib/org-api";
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
        Who is online, who auto-assignment may pick, and how many open
        conversations each member holds. Online status follows whoever has
        Connecta open.
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

  const query = await searchParams;
  const page = parsePageParam(query.page);
  const now = new Date();
  const range = parseRange(query.from, query.to, now);
  const { accessToken } = await loadSession(id);
  const [members, report] = await Promise.all([
    notFoundOnMissing(listAvailability(accessToken, id, page, ORG_PAGE_SIZE)),
    section(
      getAvailabilityReport(
        accessToken,
        id,
        range.from,
        range.to,
        MAX_PAGE_SIZE,
      ),
    ),
  ]);
  const count = pageCount(members.totalElements, members.size);
  const link = (target: number) =>
    pageHref(`/organizations/${id}/team`, {}, "page", target);

  return (
    <>
      {header}
      <TeamWorkloadTable orgId={id} members={members.content} />
      <OrgPagination
        label="Team pages"
        page={page}
        pageCount={count}
        previousHref={page > 0 ? link(page - 1) : undefined}
        nextHref={page + 1 < count ? link(page + 1) : undefined}
      />
      <section aria-labelledby="time-heading" className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 id="time-heading" className="text-lg font-medium">
            Time available
          </h2>
          <p className="text-secondary">
            How long each member was set to Available, per day. Days run on UTC.
          </p>
        </div>
        <AvailabilityRangeForm
          action={`/organizations/${id}/team`}
          range={range}
          today={now}
        />
        {report.ok ? (
          <>
            <AvailabilityTimeTable
              members={report.value.content}
              dates={report.value.content[0]?.days.map((day) => day.date) ?? []}
              today={isoDate(now)}
            />
            {report.value.totalElements > report.value.content.length && (
              <p className="text-caption text-secondary">
                Showing the first {report.value.content.length} of{" "}
                {report.value.totalElements} members.
              </p>
            )}
          </>
        ) : (
          <Alert tone="danger">
            Could not load the time available. Reload the page to try again.
          </Alert>
        )}
      </section>
    </>
  );
}
