import Link from "next/link";
import { buttonClasses } from "@/components/ui/classes";

type OrgPaginationProps = {
  /** Names the nav for screen readers, for example "Members pages". */
  label: string;
  /** Zero-based page shown now. */
  page: number;
  pageCount: number;
  previousHref?: string;
  nextHref?: string;
};

/** Previous and next links with the page position. Renders nothing when there is nowhere to go. */
export default function OrgPagination({
  label,
  page,
  pageCount,
  previousHref,
  nextHref,
}: OrgPaginationProps) {
  if (!previousHref && !nextHref) return null;

  return (
    <nav
      aria-label={label}
      className="flex items-center justify-between gap-3 text-sm"
    >
      {previousHref ? (
        <Link href={previousHref} className={buttonClasses("secondary")}>
          Previous
        </Link>
      ) : (
        <span
          aria-disabled="true"
          className={`${buttonClasses("secondary")} text-ink-muted opacity-60`}
        >
          Previous
        </span>
      )}
      <p className="text-ink-muted tabular-nums">
        Page {page + 1} of {pageCount}
      </p>
      {nextHref ? (
        <Link href={nextHref} className={buttonClasses("secondary")}>
          Next
        </Link>
      ) : (
        <span
          aria-disabled="true"
          className={`${buttonClasses("secondary")} text-ink-muted opacity-60`}
        >
          Next
        </span>
      )}
    </nav>
  );
}
