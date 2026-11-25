import Link from "next/link";
import {
  INBOX_TABS,
  INBOX_TAB_LABELS,
  inboxHref,
  type InboxFilters,
} from "@/lib/inbox-view";

type Props = { listPath: string; filters: InboxFilters };

/** Open, Closed, All. Plain links: the tab lives in the URL, keeps the search and channel, and drops the page. */
export default function InboxStatusTabs({ listPath, filters }: Props) {
  const current = filters.tab ?? "OPEN";
  return (
    <nav aria-label="Conversation status" className="flex gap-1">
      {INBOX_TABS.map((tab) => (
        <Link
          key={tab}
          href={inboxHref(listPath, {
            ...filters,
            tab: tab === "OPEN" ? undefined : tab,
          })}
          aria-current={tab === current ? "page" : undefined}
          className="inline-flex min-h-8 items-center rounded-control px-3 text-sm text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary motion-reduce:transition-none pointer-coarse:min-h-11 aria-[current=page]:bg-selected aria-[current=page]:font-medium aria-[current=page]:text-primary"
        >
          {INBOX_TAB_LABELS[tab]}
        </Link>
      ))}
    </nav>
  );
}
