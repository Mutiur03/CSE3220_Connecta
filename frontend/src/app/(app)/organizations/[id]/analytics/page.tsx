import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import AgentAnalyticsTable from "@/components/agent-analytics-table";
import AvailabilityRangeForm from "@/components/availability-range-form";
import ConversationAnalyticsReport from "@/components/conversation-analytics";
import OrgPagination from "@/components/org-pagination";
import ReaderZone from "@/components/reader-zone";
import { Alert } from "@/components/ui/alert";
import {
  getAgentAnalytics,
  getConversationAnalytics,
} from "@/lib/analytics-api";
import {
  DEFAULT_ZONE,
  parseAnalyticsRange,
  parseZone,
  todayIn,
} from "@/lib/analytics-view";
import { section } from "@/lib/inventory-view";
import { ORG_PAGE_SIZE, pageCount, parsePageParam } from "@/lib/org-pagination";
import { loadOrganization, loadSession } from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Analytics" };

const FAILED = "Could not load this report. Reload the page to try again.";

// Admin only, as on the backend: an agent has no rail entry and gets the not-found page here, with no request made.
export default async function AnalyticsPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  if (organization.callerRole !== "ADMIN") notFound();

  const query = await searchParams;
  const zone = parseZone(query.zone) ?? DEFAULT_ZONE;
  const today = todayIn(zone, new Date());
  const range = { ...parseAnalyticsRange(query.from, query.to, today), zone };
  const page = parsePageParam(query.page);
  const { accessToken } = await loadSession(id);
  // Each report fails on its own, so a backend that is not deployed yet shows a notice, not an error page.
  const [conversations, agents] = await Promise.all([
    section(getConversationAnalytics(accessToken, id, range)),
    section(getAgentAnalytics(accessToken, id, range, page, ORG_PAGE_SIZE)),
  ]);

  const action = `/organizations/${id}/analytics`;
  const count = agents.ok
    ? pageCount(agents.value.totalElements, agents.value.size)
    : 1;
  const link = (target: number) =>
    `${action}?${new URLSearchParams({ ...range, page: String(target) })}`;

  return (
    <>
      <ReaderZone />
      <header className="flex flex-col gap-1">
        <Link
          href={`/organizations/${id}`}
          className="self-start text-sm text-secondary underline-offset-4 hover:underline"
        >
          {organization.name}
        </Link>
        <h1 className="text-display font-medium">Analytics</h1>
        <p className="text-secondary">
          How many conversations came in, how fast the team answered, and who
          carried the load.
        </p>
      </header>
      <AvailabilityRangeForm
        action={action}
        range={range}
        today={today}
        keep={{ zone }}
      />
      <section
        aria-labelledby="conversations-heading"
        className="flex flex-col gap-6"
      >
        <h2 id="conversations-heading" className="sr-only">
          Conversations
        </h2>
        {conversations.ok ? (
          <ConversationAnalyticsReport report={conversations.value} />
        ) : (
          <Alert tone="danger">{FAILED}</Alert>
        )}
      </section>
      <section aria-labelledby="agents-heading" className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 id="agents-heading" className="text-lg font-medium">
            Agents
          </h2>
          <p className="text-secondary">
            Assigned, resolved, replies and time available count inside the
            range. Open now is the load at this moment.
          </p>
        </div>
        {agents.ok ? (
          <>
            <AgentAnalyticsTable agents={agents.value.content} />
            <OrgPagination
              label="Agent pages"
              page={page}
              pageCount={count}
              previousHref={page > 0 ? link(page - 1) : undefined}
              nextHref={page + 1 < count ? link(page + 1) : undefined}
            />
          </>
        ) : (
          <Alert tone="danger">{FAILED}</Alert>
        )}
      </section>
    </>
  );
}
