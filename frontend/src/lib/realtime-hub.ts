// One realtime socket per organization per tab, shared by every component that wants live events
// (presence, inbox refresher). The backend allows few sockets per user, so each subscriber opening
// its own would evict older tabs. Module state is only touched from effects, so it is SSR-safe.
import { apiBaseUrl } from "@/lib/api";
import {
  fetchRealtimeTicket,
  startRealtimeClient,
  type RealtimeEvent,
  type SocketLike,
} from "@/lib/realtime";

export type RealtimeSubscriber = {
  onEvent?: (event: RealtimeEvent) => void;
  onReconnect?: () => void;
  onUnauthorized?: () => void;
};

type Connection = {
  subscribers: Set<RealtimeSubscriber>;
  stop: () => void;
  /** Pending close after the last unsubscribe; cancelled by a quick re-subscribe. */
  closeTimer?: ReturnType<typeof setTimeout>;
};

const connections = new Map<string, Connection>();

/** Lets a strict-mode remount or a route change re-subscribe before the socket is closed. */
const CLOSE_DELAY_MS = 0;

/** A throwing subscriber must not stop the others. */
function fanOut(
  connection: Connection,
  call: (subscriber: RealtimeSubscriber) => void,
) {
  for (const subscriber of connection.subscribers) {
    try {
      call(subscriber);
    } catch (error) {
      console.error("realtime subscriber failed", error);
    }
  }
}

function open(orgId: string): Connection {
  const connection: Connection = { subscribers: new Set(), stop: () => {} };
  connection.stop = startRealtimeClient({
    apiBase: apiBaseUrl(),
    createSocket: (url) => new WebSocket(url) as SocketLike,
    fetchTicket: () => fetchRealtimeTicket(orgId),
    onEvent: (event) => fanOut(connection, (s) => s.onEvent?.(event)),
    onReconnect: () => fanOut(connection, (s) => s.onReconnect?.()),
    onUnauthorized: () => fanOut(connection, (s) => s.onUnauthorized?.()),
  });
  return connection;
}

/** Joins the organization's shared socket (opened on the first subscriber). Returns the unsubscribe. */
export function subscribeRealtime(
  orgId: string,
  subscriber: RealtimeSubscriber,
): () => void {
  let connection = connections.get(orgId);
  if (!connection) {
    connection = open(orgId);
    connections.set(orgId, connection);
  }
  clearTimeout(connection.closeTimer);
  connection.closeTimer = undefined;
  const joined = connection;
  joined.subscribers.add(subscriber);

  return () => {
    joined.subscribers.delete(subscriber);
    if (joined.subscribers.size > 0) return;
    joined.closeTimer = setTimeout(() => {
      if (joined.subscribers.size > 0) return;
      joined.stop();
      if (connections.get(orgId) === joined) connections.delete(orgId);
    }, CLOSE_DELAY_MS);
  };
}
