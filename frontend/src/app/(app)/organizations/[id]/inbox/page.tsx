import type { Metadata } from "next";
import Link from "next/link";
import { InboxIcon } from "@/components/icons";
import ConversationList from "@/components/conversation-list";
import InboxFilterBar from "@/components/inbox-filter-bar";
import OrgPagination from "@/components/org-pagination";
import RealtimeInboxRefresher from "@/components/realtime-inbox-refresher";
import { listConversations } from "@/lib/inbox-api";
import {
  hasInboxFilters,
  inboxHref,
  parseInboxFilters,
} from "@/lib/inbox-view";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageRange,
  parsePageParam,
} from "@/lib/org-pagination";
import { loadOrganization, loadSession, notFoundOnMissing } from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Inbox" };

export default async function InboxPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const query = await searchParams;
  const page = parsePageParam(query.page);
  const filters = parseInboxFilters(query);
  const { accessToken } = await loadSession(id);
  const [organization, conversations] = await Promise.all([
    loadOrganization(id),
    notFoundOnMissing(
      listConversations(accessToken, id, filters, page, ORG_PAGE_SIZE),
    ),
  ]);

  const listPath = `/organizations/${id}/inbox`;
  const filtered = hasInboxFilters(filters);
  const range = pageRange(
    page,
    conversations.size,
    conversations.totalElements,
  );
  const count = pageCount(conversations.totalElements, conversations.size);
  const pageLink = (target: number) => inboxHref(listPath, filters, target);

  return (
    <>
      <RealtimeInboxRefresher orgId={id} />
      <header className="flex flex-col gap-1">
        <Link
          href={`/organizations/${id}`}
          className="self-start text-sm text-secondary underline-offset-4 hover:underline"
        >
          {organization.name}
        </Link>
        <h1 className="text-display font-medium">Inbox</h1>
        <p className="text-secondary">
          Customer conversations from Instagram and Messenger, newest first
        </p>
      </header>

      <section aria-labelledby="inbox-heading" className="flex flex-col gap-4">
        <h2 id="inbox-heading" className="sr-only">
          Conversation list
        </h2>
        {(conversations.totalElements > 0 || filtered) && (
          <InboxFilterBar listPath={listPath} filters={filters} />
        )}
        {conversations.content.length === 0 ? (
          <div className="flex flex-wrap items-start gap-x-4 gap-y-3 rounded-control border border-line p-4 sm:p-5">
            <span className="grid size-10 shrink-0 place-items-center rounded-control border border-line bg-app text-primary">
              <InboxIcon />
            </span>
            <div className="flex min-w-0 flex-1 basis-56 flex-col gap-1">
              <p className="font-medium">
                {filtered
                  ? "No conversations match these filters"
                  : "No conversations yet"}
              </p>
              <p className="max-w-prose text-sm text-secondary">
                {filtered
                  ? "Search for another name or choose a different channel."
                  : "Messages that customers send to a connected Instagram or Messenger account appear here."}
              </p>
            </div>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <ConversationList
              orgId={id}
              conversations={conversations.content}
              now={new Date()}
            />
            <div className="flex flex-wrap items-center justify-between gap-3">
              {range && (
                <p className="figure text-caption text-secondary">
                  {range.from}–{range.to} of {conversations.totalElements}{" "}
                  conversations
                </p>
              )}
              <OrgPagination
                label="Conversation pages"
                page={page}
                pageCount={count}
                previousHref={page > 0 ? pageLink(page - 1) : undefined}
                nextHref={page + 1 < count ? pageLink(page + 1) : undefined}
              />
            </div>
          </div>
        )}
      </section>
    </>
  );
}
