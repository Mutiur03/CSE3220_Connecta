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

export const MESSAGES_PAGE_SIZE = 30;

/** The backend sends the newest message first; a thread reads oldest at the top. */
export const readingOrder = <T>(newestFirst: readonly T[]): T[] =>
  [...newestFirst].reverse();

/** Up to two initials of a display name; "?" when there is no name. */
export const avatarInitials = (name: string | null) => {
  const letters = (name ?? "")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
  return letters || "?";
};

const AVATAR_TONES = ["bg-hover", "bg-selected"] as const;

/** One of two neutral surface tokens, chosen from the name so a person keeps the same tone. */
export const avatarTone = (name: string | null) => {
  let hash = 0;
  for (const char of name?.trim() ?? "") {
    hash = (hash * 31 + char.charCodeAt(0)) % AVATAR_TONES.length;
  }
  return AVATAR_TONES[hash];
};

/** The customer names on a thread, comma separated, or the unknown label. */
export const participantsLabel = (
  participants: readonly { displayName: string | null }[],
) => {
  const names = participants
    .map((participant) => participant.displayName?.trim())
    .filter((name): name is string => Boolean(name));
  return names.length > 0 ? names.join(", ") : UNKNOWN_PARTICIPANT;
};

/**
 * Thread URL. It carries the list filters and list page, so the list pane beside the thread stays where it
 * was; `history` pages back through older messages. Unset parts are left out.
 */
export function threadHref(
  threadPath: string,
  filters: InboxFilters = {},
  listPage = 0,
  history = 0,
): string {
  const query = new URLSearchParams(inboxListQuery(filters, listPage));
  if (history > 0) query.set("history", String(history));
  const search = query.toString();
  return search ? `${threadPath}?${search}` : threadPath;
}

export const nextStatus = (status: "OPEN" | "CLOSED") =>
  status === "OPEN" ? "CLOSED" : "OPEN";
