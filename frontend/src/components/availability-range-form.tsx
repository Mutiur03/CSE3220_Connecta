import Link from "next/link";
import {
  QUICK_RANGE_DAYS,
  lastDays,
  type DateRange,
} from "@/lib/availability-report-view";
import { buttonClass } from "./org-styles";
import { fieldClasses } from "./ui/classes";

type Props = {
  action: string;
  range: DateRange;
  today: Date;
  /** Other query values the form and the quick ranges carry along, such as the reader's time zone. */
  keep?: Record<string, string>;
};

/** A plain GET form: the range lives in the URL, so the page stays a server component. */
export default function AvailabilityRangeForm({
  action,
  range,
  today,
  keep = {},
}: Props) {
  return (
    <form
      // Remount on a new range: a quick-range link is a client navigation that keeps the inputs, and their
      // defaultValue would still show the old dates.
      key={`${range.from}/${range.to}`}
      method="get"
      action={action}
      className="flex flex-wrap items-end gap-x-4 gap-y-3"
    >
      {Object.entries(keep).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-secondary">From</span>
        <input
          type="date"
          name="from"
          required
          defaultValue={range.from}
          className={`${fieldClasses} figure w-auto`}
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        <span className="text-secondary">To</span>
        <input
          type="date"
          name="to"
          required
          defaultValue={range.to}
          className={`${fieldClasses} figure w-auto`}
        />
      </label>
      <button type="submit" className={buttonClass("primary")}>
        Apply
      </button>
      <nav aria-label="Quick ranges" className="flex gap-4 pb-2.5 text-sm">
        {QUICK_RANGE_DAYS.map((days) => {
          const quick = lastDays(today, days);
          return (
            <Link
              key={days}
              href={`${action}?${new URLSearchParams({ ...quick, ...keep })}`}
              className="text-secondary underline-offset-4 transition-colors duration-150 ease-out hover:text-primary hover:underline motion-reduce:transition-none"
            >
              Last {days} days
            </Link>
          );
        })}
      </nav>
    </form>
  );
}
