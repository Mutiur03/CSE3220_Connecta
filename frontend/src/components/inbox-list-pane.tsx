import type { Page } from "@/lib/org-api";
import type { Conversation } from "@/lib/inbox-api";
import {
  hasInboxFilters,
  inboxHref,
  type InboxFilters,
} from "@/lib/inbox-view";
import { pageCount, pageRange } from "@/lib/org-pagination";
import ConversationList from "./conversation-list";
import InboxFilterBar from "./inbox-filter-bar";
import InboxStatusTabs from "./inbox-status-tabs";
import OrgPagination from "./org-pagination";

type Props = {
  orgId: string;
  filters: InboxFilters;
  page: number;
  conversations: Page<Conversation>;
  /** The open conversation, if any. */
  selectedId?: string;
  /** h1 on the inbox itself; h2 beside a thread, where the contact name is the page heading. */
  headingLevel: 1 | 2;
  now: Date;
};

function emptyState(filtered: boolean, tab: InboxFilters["tab"]) {
  if (filtered) {
    return {
      title: "No conversations match these filters",
      detail: "Search for another name or choose a different channel.",
    };
  }
  if (tab === "CLOSED") {
    return {
      title: "No closed conversations",
      detail:
        "Conversations you close move here, and reopen when you need them.",
    };
  }
  if (tab === "ALL") {
    return {
      title: "No conversations yet",
      detail:
        "Messages that customers send to a connected Instagram or Messenger account appear here.",
    };
  }
  return {
    title: "No open conversations",
    detail:
      "New messages from Instagram and Messenger customers open a conversation here. Closed ones are under Closed.",
  };
}

/** The left pane: title, filters, the scrolling conversation rows and the page links at the bottom. */
export default function InboxListPane({
  orgId,
  filters,
  page,
  conversations,
  selectedId,
  headingLevel,
  now,
}: Props) {
  const listPath = `/organizations/${orgId}/inbox`;
  const filtered = hasInboxFilters(filters);
  const range = pageRange(
    page,
    conversations.size,
    conversations.totalElements,
  );
  const count = pageCount(conversations.totalElements, conversations.size);
  const pageLink = (target: number) => inboxHref(listPath, filters, target);
  const Heading = headingLevel === 1 ? "h1" : "h2";
  const empty = emptyState(filtered, filters.tab);

  return (
    <section
      aria-labelledby="inbox-heading"
      className="flex min-h-0 flex-1 flex-col"
    >
      <header className="flex flex-col gap-3 border-b border-line p-4">
        <div className="flex items-baseline justify-between gap-3">
          <Heading id="inbox-heading" className="text-title font-semibold">
            Inbox
          </Heading>
          {range && (
            <p className="figure text-caption text-secondary">
              {range.from}–{range.to} of {conversations.totalElements}
            </p>
          )}
        </div>
        <InboxStatusTabs listPath={listPath} filters={filters} />
        {(conversations.totalElements > 0 || filtered) && (
          <InboxFilterBar listPath={listPath} filters={filters} />
        )}
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
        {conversations.content.length === 0 ? (
          <div className="flex flex-col gap-1 p-4">
            <p className="font-medium">{empty.title}</p>
            <p className="text-sm text-secondary">{empty.detail}</p>
          </div>
        ) : (
          <ConversationList
            orgId={orgId}
            conversations={conversations.content}
            now={now}
            selectedId={selectedId}
            filters={filters}
            listPage={page}
          />
        )}
      </div>

      {count > 1 && (
        <footer className="border-t border-line p-3">
          <OrgPagination
            label="Conversation pages"
            page={page}
            pageCount={count}
            previousHref={page > 0 ? pageLink(page - 1) : undefined}
            nextHref={page + 1 < count ? pageLink(page + 1) : undefined}
          />
        </footer>
      )}
    </section>
  );
}
