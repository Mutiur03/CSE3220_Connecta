import Link from "next/link";
import {
  INBOX_ASSIGNEE_LABELS,
  inboxHref,
  unassignedLabel,
  type InboxAssignee,
  type InboxFilters,
} from "@/lib/inbox-view";

type Props = {
  listPath: string;
  filters: InboxFilters;
  /** Null when the count could not be fetched: the label then shows without it. */
  unassignedCount: number | null;
};

const OPTIONS: { value: InboxAssignee | undefined; key: string }[] = [
  { value: undefined, key: "everyone" },
  { value: "me", key: "me" },
  { value: "none", key: "none" },
];

/** Anyone, Mine, Unassigned (n). Plain links: the choice lives in the URL and drops the page. */
export default function InboxAssigneeTabs({
  listPath,
  filters,
  unassignedCount,
}: Props) {
  return (
    <nav aria-label="Assignee" className="flex flex-wrap gap-1">
      {OPTIONS.map(({ value, key }) => (
        <Link
          key={key}
          href={inboxHref(listPath, { ...filters, assignee: value })}
          aria-current={value === filters.assignee ? "page" : undefined}
          className="inline-flex min-h-8 items-center rounded-control px-3 text-sm text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary motion-reduce:transition-none pointer-coarse:min-h-11 aria-[current=page]:bg-selected aria-[current=page]:font-medium aria-[current=page]:text-primary"
        >
          {value === "none"
            ? unassignedLabel(unassignedCount)
            : INBOX_ASSIGNEE_LABELS[key as keyof typeof INBOX_ASSIGNEE_LABELS]}
        </Link>
      ))}
    </nav>
  );
}
