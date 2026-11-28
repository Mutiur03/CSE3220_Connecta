/** The backend rejects a longer range, counted in days with both ends included. */
export const MAX_RANGE_DAYS = 31;
export const DEFAULT_RANGE_DAYS = 7;
export const QUICK_RANGE_DAYS = [7, 14, 30] as const;

const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
const DAY_MS = 86_400_000;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export type DateRange = { from: string; to: string };

/** "2h 15m", "45m", or an en dash for no time. Seconds below a minute are dropped. */
export function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / SECONDS_PER_MINUTE);
  if (minutes <= 0) return "–";
  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  const rest = minutes % MINUTES_PER_HOUR;
  if (hours === 0) return `${rest}m`;
  return `${hours}h ${rest}m`;
}

export const isoDate = (date: Date): string => date.toISOString().slice(0, 10);

const shift = (iso: string, days: number): string =>
  isoDate(new Date(Date.parse(iso) + days * DAY_MS));

function parseDate(raw: string | string[] | undefined): string | null {
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (!value || !ISO_DATE.test(value)) return null;
  const time = Date.parse(value);
  return Number.isNaN(time) || isoDate(new Date(time)) !== value ? null : value;
}

/** The last `days` UTC days ending today. */
export function lastDays(today: Date, days: number): DateRange {
  const to = isoDate(today);
  return { from: shift(to, 1 - days), to };
}

/**
 * The range from the URL. Anything malformed or reversed gives the default (a week unless the caller
 * names its own), and a range over the limit keeps its end date and trims the start, so a hand-typed URL never produces a 400 page.
 */
export function parseRange(
  rawFrom: string | string[] | undefined,
  rawTo: string | string[] | undefined,
  today: Date,
  { defaultDays = DEFAULT_RANGE_DAYS, maxDays = MAX_RANGE_DAYS } = {},
): DateRange {
  const from = parseDate(rawFrom);
  const to = parseDate(rawTo);
  if (!from || !to || from > to) return lastDays(today, defaultDays);
  const earliest = shift(to, 1 - maxDays);
  return { from: from < earliest ? earliest : from, to };
}

/** "Mon 24" for a UTC date. */
export function dayHeading(iso: string): string {
  const date = new Date(Date.parse(iso));
  const weekday = date.toLocaleDateString("en", {
    weekday: "short",
    timeZone: "UTC",
  });
  return `${weekday} ${date.getUTCDate()}`;
}
