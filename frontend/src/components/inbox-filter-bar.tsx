import Link from "next/link";
import {
  INBOX_CHANNELS,
  INBOX_CHANNEL_LABELS,
  INBOX_QUERY_MAX_LENGTH,
  hasInboxFilters,
  inboxHref,
  type InboxFilters,
} from "@/lib/inbox-view";
import { buttonClasses, fieldClasses } from "./ui/classes";
import { Select } from "./ui/select";

type Props = { listPath: string; filters: InboxFilters };

// A plain GET form: the filters live in the URL, and submitting drops the page number.
// Laid out for the 340px list pane: search on its own line, channel and Apply below.
export default function InboxFilterBar({ listPath, filters }: Props) {
  return (
    <form
      role="search"
      aria-label="Filter conversations"
      method="get"
      action={listPath}
      className="flex flex-wrap items-center gap-2"
    >
      {filters.tab && <input type="hidden" name="status" value={filters.tab} />}
      {filters.assignee && (
        <input type="hidden" name="assignee" value={filters.assignee} />
      )}
      <div className="flex basis-full items-center gap-2">
        <label htmlFor="inbox-search" className="sr-only">
          Search
        </label>
        <input
          id="inbox-search"
          name="q"
          type="search"
          defaultValue={filters.q ?? ""}
          placeholder="Search by name"
          maxLength={INBOX_QUERY_MAX_LENGTH}
          autoComplete="off"
          className={`${fieldClasses} [&::-webkit-search-cancel-button]:appearance-none`}
        />
        {filters.q && (
          <Link
            href={inboxHref(listPath, { ...filters, q: undefined })}
            aria-label="Clear search"
            className={buttonClasses("secondary")}
          >
            Clear
          </Link>
        )}
      </div>
      <div className="flex min-w-0 flex-1">
        <label htmlFor="inbox-channel" className="sr-only">
          Channel
        </label>
        <Select
          id="inbox-channel"
          name="channel"
          autoComplete="off"
          defaultValue={filters.channel ?? ""}
        >
          <option value="">All channels</option>
          {INBOX_CHANNELS.map((channel) => (
            <option key={channel} value={channel}>
              {INBOX_CHANNEL_LABELS[channel]}
            </option>
          ))}
        </Select>
      </div>
      <div className="flex items-center gap-3">
        <button type="submit" className={buttonClasses("secondary")}>
          Apply
        </button>
        {hasInboxFilters(filters) && (
          <Link
            href={inboxHref(listPath, {
              tab: filters.tab,
              assignee: filters.assignee,
            })}
            className="text-sm text-secondary underline-offset-4 hover:text-primary hover:underline"
          >
            Reset filters
          </Link>
        )}
      </div>
    </form>
  );
}
