import {
  formatDuration,
  parseRange,
  type DateRange,
} from "./availability-report-view";

/** docs/analytics-metrics.md: the default range is the last 30 days, and the backend rejects more than 366. */
export const ANALYTICS_DEFAULT_DAYS = 30;
export const ANALYTICS_MAX_DAYS = 366;
export const DEFAULT_ZONE = "UTC";

const SECONDS_PER_MINUTE = 60;

const first = (raw: string | string[] | undefined) =>
  Array.isArray(raw) ? raw[0] : raw;

/** An IANA zone the runtime knows, else undefined (the backend answers 400 for an unknown one). */
export function parseZone(
  raw: string | string[] | undefined,
): string | undefined {
  const zone = first(raw);
  if (!zone) return undefined;
  try {
    new Intl.DateTimeFormat("en", { timeZone: zone });
    return zone;
  } catch {
    return undefined;
  }
}

/** Today's date in `zone`, as a Date at UTC midnight so the shared range helpers see that day. */
export function todayIn(zone: string, now: Date): Date {
  const iso = new Intl.DateTimeFormat("en-CA", { timeZone: zone }).format(now);
  return new Date(`${iso}T00:00:00Z`);
}

/** The range from the URL, defaulting to the last 30 days in the reader's zone. */
export const parseAnalyticsRange = (
  rawFrom: string | string[] | undefined,
  rawTo: string | string[] | undefined,
  today: Date,
): DateRange =>
  parseRange(rawFrom, rawTo, today, {
    defaultDays: ANALYTICS_DEFAULT_DAYS,
    maxDays: ANALYTICS_MAX_DAYS,
  });

/** "45s", "9m", "2h 15m", or an en dash when there is nothing to measure. */
export function formatSeconds(seconds: number | null): string {
  if (seconds === null) return "–";
  if (seconds > 0 && seconds < SECONDS_PER_MINUTE) return `${seconds}s`;
  return formatDuration(seconds);
}

/** A bar's length as a percentage of the largest value, with a sliver kept for any non-zero count. */
export function barPercent(count: number, max: number): number {
  if (count <= 0 || max <= 0) return 0;
  return Math.max(2, Math.round((count / max) * 100));
}

/** "Nov 3" for an ISO date, read as a calendar day (no zone shift). */
export const shortDate = (iso: string): string =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString("en", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
