export const INBOX_QUERY_MAX_LENGTH = 100;

export const INBOX_CHANNELS = ["INSTAGRAM", "MESSENGER"] as const;
export type InboxChannel = (typeof INBOX_CHANNELS)[number];

export const INBOX_CHANNEL_LABELS: Record<InboxChannel, string> = {
  INSTAGRAM: "Instagram",
  MESSENGER: "Messenger",
};

export const UNKNOWN_PARTICIPANT = "Unknown contact";

export type InboxFilters = { q?: string; channel?: InboxChannel };

type SearchParams = Record<string, string | string[] | undefined>;

const firstValue = (raw: string | string[] | undefined) =>
  (Array.isArray(raw) ? raw[0] : raw) ?? "";

/** The filters the URL asks for. An unknown channel is dropped, so the backend never answers 400. */
export function parseInboxFilters(params: SearchParams): InboxFilters {
  const q = firstValue(params.q).trim().slice(0, INBOX_QUERY_MAX_LENGTH);
  const channel = firstValue(params.channel);
  return {
    q: q || undefined,
    channel: INBOX_CHANNELS.find((known) => known === channel),
  };
}

export const hasInboxFilters = (filters: InboxFilters) =>
  Boolean(filters.q || filters.channel);

/** Query string shared by the page URL and the API call. Unset filters and page 0 are left out. */
export function inboxListQuery(
  filters: InboxFilters,
  page = 0,
  size?: number,
): string {
  const query = new URLSearchParams();
  if (filters.q) query.set("q", filters.q);
  if (filters.channel) query.set("channel", filters.channel);
  if (page > 0) query.set("page", String(page));
  if (size !== undefined) query.set("size", String(size));
  return query.toString();
}

export function inboxHref(
  listPath: string,
  filters: InboxFilters,
  page = 0,
): string {
  const search = inboxListQuery(filters, page);
  return search ? `${listPath}?${search}` : listPath;
}

export const participantLabel = (name: string | null) =>
  name?.trim() || UNKNOWN_PARTICIPANT;
