import { PAGE_SIZE } from "./org-api";

/** Rows per page on the organization pages. The backend allows up to 100. */
export const ORG_PAGE_SIZE = PAGE_SIZE;

/** Keeps a hand-typed page number inside what the backend accepts as an int. */
export const MAX_PAGE_INDEX = 10_000;

/**
 * A zero-based page index from a URL search param. Anything that is not a whole number from zero up
 * shows the first page, and a huge number is capped.
 */
export function parsePageParam(raw: string | string[] | undefined): number {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const page = Number(value);
  if (!Number.isSafeInteger(page) || page < 0) return 0;
  return Math.min(page, MAX_PAGE_INDEX);
}

/** At least one page, so an empty list still reads as page 1 of 1. */
export function pageCount(totalElements: number, size: number): number {
  return Math.max(1, Math.ceil(totalElements / size));
}

/** The 1-based rows shown on this page, or null when the page is empty. */
export function pageRange(
  page: number,
  size: number,
  totalElements: number,
): { from: number; to: number } | null {
  if (totalElements === 0) return null;
  const from = page * size + 1;
  if (from > totalElements) return null;
  return { from, to: Math.min(totalElements, (page + 1) * size) };
}

/**
 * The URL for one page of one list. The other lists keep their current page, and page 0 is left out
 * of the query so the first page has a clean address.
 */
export function pageHref(
  pathname: string,
  current: Record<string, number>,
  key: string,
  page: number,
): string {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries({ ...current, [key]: page })) {
    if (value > 0) query.set(name, String(value));
  }
  const search = query.toString();
  return search ? `${pathname}?${search}` : pathname;
}
