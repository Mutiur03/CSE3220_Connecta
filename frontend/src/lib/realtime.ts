// Pure logic for the live inbox socket (backend protocol: README "Realtime"). No React here, so it
// is testable with a fake socket and fake timers.

export type RealtimeEvent = {
  type: "message.created";
  conversationId: string;
  messageId: string;
  channel: string;
  direction: string;
  newConversation: boolean;
};

export type SocketLike = {
  onopen: (() => void) | null;
  onmessage: ((event: { data: unknown }) => void) | null;
  onclose: ((event: { code: number }) => void) | null;
  send(data: string): void;
  close(): void;
};

export const PING_INTERVAL_MS = 30_000;
export const REFRESH_DEBOUNCE_MS = 250;
/** A connection must stay open this long before the reconnect backoff starts over. */
export const STABLE_OPEN_MS = 10_000;
/** After this many consecutive 401s from the ticket route the client stops asking until the tab is shown again. */
export const MAX_CONSECUTIVE_UNAUTHORIZED = 3;
const BACKOFF_BASE_MS = 1_000;
const BACKOFF_CAP_MS = 30_000;
const REPLACED_BACKOFF_MS = 60_000;
/** Close code the server uses when a newer session of the same user replaced this one. */
const CLOSE_REPLACED = 4002;
const PING_FRAME = "ping";

/** http(s) API base to the ws(s) endpoint of the inbox socket. */
export function realtimeUrl(apiBase: string, ticket: string): string {
  const url = new URL("/ws/inbox", apiBase);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.searchParams.set("ticket", ticket);
  return url.toString();
}

/** Exponential delay with jitter (50-100% of the step), capped. `random` is injectable for tests. */
export function backoffDelay(
  attempt: number,
  closeCode?: number,
  random: () => number = Math.random,
): number {
  const step =
    closeCode === CLOSE_REPLACED
      ? REPLACED_BACKOFF_MS
      : Math.min(BACKOFF_CAP_MS, BACKOFF_BASE_MS * 2 ** attempt);
  return Math.round(step * (0.5 + random() * 0.5));
}

/** The event, or null for pong frames, malformed JSON and anything that is not a message event. */
export function parseRealtimeEvent(raw: unknown): RealtimeEvent | null {
  if (typeof raw !== "string") return null;
  let value: unknown;
  try {
    value = JSON.parse(raw);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null) return null;
  const e = value as Record<string, unknown>;
  if (
    e.type !== "message.created" ||
    typeof e.conversationId !== "string" ||
    typeof e.messageId !== "string"
  ) {
    return null;
  }
  return {
    type: e.type,
    conversationId: e.conversationId,
    messageId: e.messageId,
    channel: String(e.channel ?? ""),
    direction: String(e.direction ?? ""),
    newConversation: e.newConversation === true,
  };
}

/** The list (no conversation) wants every event; a thread only its own conversation's. */
export function isRelevant(event: RealtimeEvent, conversationId?: string) {
  return !conversationId || event.conversationId === conversationId;
}

/** Runs `fn` once after `ms` of quiet, however many times `call` was hit. */
export function coalesce(fn: () => void, ms: number) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  return {
    call() {
      clearTimeout(timer);
      timer = setTimeout(() => {
        timer = undefined;
        fn();
      }, ms);
    },
    cancel() {
      clearTimeout(timer);
      timer = undefined;
    },
  };
}

/** Thrown by `fetchTicket` when the ticket route answered 401 (an expired session). */
export class TicketUnauthorizedError extends Error {}

/** Calls `onShown` each time the tab becomes visible; returns the unsubscribe. */
function onTabShown(onShown: () => void): () => void {
  if (typeof document === "undefined") return () => {};
  const listener = () => {
    if (document.visibilityState === "visible") onShown();
  };
  document.addEventListener("visibilitychange", listener);
  return () => document.removeEventListener("visibilitychange", listener);
}

export type RealtimeClientOptions = {
  /** A fresh single-use ticket. Rejects when none can be had; throws `TicketUnauthorizedError` on 401. */
  fetchTicket: () => Promise<string>;
  createSocket: (url: string) => SocketLike;
  apiBase: string;
  onEvent: (event: RealtimeEvent) => void;
  /** After any connection that follows an earlier one: events in between were not replayed. */
  onReconnect: () => void;
  /** Called for each 401 up to the cap, so the page can renew the session. */
  onUnauthorized?: () => void;
  /** Wakes a client that gave up after repeated 401s. Defaults to the tab becoming visible. */
  subscribeShown?: (onShown: () => void) => () => void;
  random?: () => number;
};

/** Keeps one socket alive: ticket, connect, ping, reconnect with backoff. Returns a stop function. */
export function startRealtimeClient(
  options: RealtimeClientOptions,
): () => void {
  const {
    fetchTicket,
    createSocket,
    apiBase,
    onEvent,
    onReconnect,
    onUnauthorized,
    subscribeShown = onTabShown,
    random,
  } = options;
  let stopped = false;
  let attempt = 0;
  let everConnected = false;
  let socket: SocketLike | undefined;
  let pingTimer: ReturnType<typeof setInterval> | undefined;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let stableTimer: ReturnType<typeof setTimeout> | undefined;
  let unauthorized = 0;
  let unsubscribeShown: (() => void) | undefined;

  const retry = (closeCode?: number) => {
    if (stopped) return;
    retryTimer = setTimeout(
      connect,
      backoffDelay(attempt++, closeCode, random),
    );
  };

  /** A dead session: stop retrying and refreshing until the tab is shown again. */
  const giveUp = () => {
    unsubscribeShown ??= subscribeShown(() => {
      unsubscribeShown?.();
      unsubscribeShown = undefined;
      unauthorized = 0;
      attempt = 0;
      void connect();
    });
  };

  async function connect() {
    let ticket: string;
    try {
      ticket = await fetchTicket();
    } catch (error) {
      if (stopped) return;
      if (error instanceof TicketUnauthorizedError) {
        if (++unauthorized > MAX_CONSECUTIVE_UNAUTHORIZED) {
          giveUp();
          return;
        }
        onUnauthorized?.();
      }
      retry();
      return;
    }
    if (stopped) return;
    unauthorized = 0;
    const opened = createSocket(realtimeUrl(apiBase, ticket));
    socket = opened;
    opened.onopen = () => {
      stableTimer = setTimeout(() => (attempt = 0), STABLE_OPEN_MS);
      if (everConnected) onReconnect();
      everConnected = true;
      pingTimer = setInterval(() => opened.send(PING_FRAME), PING_INTERVAL_MS);
    };
    opened.onmessage = ({ data }) => {
      const event = parseRealtimeEvent(data);
      if (event) onEvent(event);
    };
    opened.onclose = ({ code }) => {
      clearInterval(pingTimer);
      clearTimeout(stableTimer);
      retry(code);
    };
  }

  void connect();
  return () => {
    stopped = true;
    clearInterval(pingTimer);
    clearTimeout(retryTimer);
    clearTimeout(stableTimer);
    unsubscribeShown?.();
    if (socket) {
      socket.onclose = null;
      socket.close();
    }
  };
}
