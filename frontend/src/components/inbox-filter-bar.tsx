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
export default function InboxFilterBar({ listPath, filters }: Props) {
  return (
    <form
      role="search"
      aria-label="Filter conversations"
      method="get"
      action={listPath}
      className="flex flex-wrap items-center gap-2"
    >
      <div className="flex min-w-56 flex-[2_1_16rem] items-center gap-2">
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
      <div className="flex min-w-40 flex-[1_1_10rem]">
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
            href={listPath}
            className="text-sm text-secondary underline-offset-4 hover:text-primary hover:underline"
          >
            Reset filters
          </Link>
        )}
      </div>
    </form>
  );
}
