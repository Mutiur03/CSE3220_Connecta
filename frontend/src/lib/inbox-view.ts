export const INBOX_QUERY_MAX_LENGTH = 100;

export const INBOX_CHANNELS = ["INSTAGRAM", "MESSENGER"] as const;
export type InboxChannel = (typeof INBOX_CHANNELS)[number];

export const INBOX_CHANNEL_LABELS: Record<InboxChannel, string> = {
  INSTAGRAM: "Instagram",
  MESSENGER: "Messenger",
};

export const UNKNOWN_PARTICIPANT = "Unknown contact";

/** The list tabs. Open is the default and is left out of the URL. */
export const INBOX_TABS = ["OPEN", "CLOSED", "ALL"] as const;
export type InboxTab = (typeof INBOX_TABS)[number];

export const INBOX_TAB_LABELS: Record<InboxTab, string> = {
  OPEN: "Open",
  CLOSED: "Closed",
  ALL: "All",
};

/** Who holds the conversation. Everyone is the default and is left out of the URL. */
export const INBOX_ASSIGNEES = ["me", "none"] as const;
export type InboxAssignee = (typeof INBOX_ASSIGNEES)[number];

export const INBOX_ASSIGNEE_LABELS = {
  everyone: "Everyone",
  me: "Assigned to me",
  none: "Unassigned",
} as const;

/** `tab` undefined means the Open tab; `assignee` undefined means everyone. */
export type InboxFilters = {
  q?: string;
  channel?: InboxChannel;
  tab?: InboxTab;
  assignee?: InboxAssignee;
};

/** The status the API filters on: OPEN by default, nothing for the All tab. */
export const apiStatus = (tab: InboxTab | undefined) =>
  tab === "ALL" ? undefined : (tab ?? "OPEN");

type SearchParams = Record<string, string | string[] | undefined>;

const firstValue = (raw: string | string[] | undefined) =>
  (Array.isArray(raw) ? raw[0] : raw) ?? "";

/** The filters the URL asks for. An unknown channel is dropped, so the backend never answers 400. */
export function parseInboxFilters(params: SearchParams): InboxFilters {
  const q = firstValue(params.q).trim().slice(0, INBOX_QUERY_MAX_LENGTH);
  const channel = firstValue(params.channel);
  const status = firstValue(params.status).toUpperCase();
  const assignee = firstValue(params.assignee).toLowerCase();
  return {
    q: q || undefined,
    channel: INBOX_CHANNELS.find((known) => known === channel),
    tab: INBOX_TABS.find((known) => known !== "OPEN" && known === status),
    assignee: INBOX_ASSIGNEES.find((known) => known === assignee),
  };
}

export const hasInboxFilters = (filters: InboxFilters) =>
  Boolean(filters.q || filters.channel || filters.assignee);

/** "Unassigned (3)", or just the label when the count is unknown. */
export const unassignedLabel = (count: number | null) =>
  count === null
    ? INBOX_ASSIGNEE_LABELS.none
    : `${INBOX_ASSIGNEE_LABELS.none} (${count})`;

type Held = {
  status: "OPEN" | "CLOSED";
  assignee: { online: boolean } | null;
};

/** An open conversation whose holder is offline: nobody is there to answer it. */
export const heldByOffline = (conversation: Held) =>
  conversation.status === "OPEN" && conversation.assignee?.online === false;

/** The page URL's query string. Unset filters, the Open tab and page 0 are left out. */
export function inboxListQuery(
  filters: InboxFilters,
  page = 0,
  size?: number,
): string {
  const query = new URLSearchParams();
  if (filters.q) query.set("q", filters.q);
  if (filters.channel) query.set("channel", filters.channel);
  if (filters.assignee) query.set("assignee", filters.assignee);
  if (filters.tab) query.set("status", filters.tab);
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

/** Messages from one side closer together than this read as one group. */
export const GROUP_GAP_MS = 5 * 60_000;

type Groupable = {
  id: string;
  direction: "INBOUND" | "OUTBOUND";
  sentAt: string;
  senderId: string | null;
  senderName: string | null;
};

export type MessageGroup<T> = {
  key: string;
  outbound: boolean;
  senderName: string | null;
  messages: T[];
};

export type ThreadDay<T> = {
  key: string;
  label: string;
  groups: MessageGroup<T>[];
};

const dayKey = (date: Date) =>
  `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;

/** "Today", "Yesterday", else the full date, so a day break reads without a clock. */
export function dayLabel(iso: string, now: Date): string {
  const day = dayKey(new Date(iso));
  if (day === dayKey(now)) return "Today";
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (day === dayKey(yesterday)) return "Yesterday";
  return new Date(iso).toLocaleDateString("en", {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

const sameGroup = (previous: Groupable, next: Groupable) =>
  previous.direction === next.direction &&
  previous.senderId === next.senderId &&
  new Date(next.sentAt).getTime() - new Date(previous.sentAt).getTime() <=
    GROUP_GAP_MS;

/**
 * A thread in reading order, split into days and, inside a day, into groups of consecutive messages from the
 * same sender a few minutes apart. Each group shows its time once.
 */
export function groupThread<T extends Groupable>(
  oldestFirst: readonly T[],
  now: Date,
): ThreadDay<T>[] {
  const days: ThreadDay<T>[] = [];
  for (const message of oldestFirst) {
    const key = dayKey(new Date(message.sentAt));
    let day = days.at(-1);
    if (day?.key !== key) {
      day = { key, label: dayLabel(message.sentAt, now), groups: [] };
      days.push(day);
    }
    const group = day.groups.at(-1);
    const last = group?.messages.at(-1);
    if (group && last && sameGroup(last, message)) {
      group.messages.push(message);
    } else {
      day.groups.push({
        key: message.id,
        outbound: message.direction === "OUTBOUND",
        senderName: message.senderName,
        messages: [message],
      });
    }
  }
  return days;
}
