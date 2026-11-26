"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { apiBaseUrl } from "@/lib/api";
import {
  coalesce,
  fetchRealtimeTicket,
  isRelevant,
  REFRESH_DEBOUNCE_MS,
  startRealtimeClient,
  type SocketLike,
} from "@/lib/realtime";

/**
 * Renders nothing. Holds the organization's live socket and, on a relevant message event, asks the
 * server components to re-fetch (router.refresh), so there is no client-side message state.
 * Without `conversationId` every event refreshes (inbox list); with it only that conversation does.
 */
export default function RealtimeInboxRefresher({
  orgId,
  conversationId,
}: {
  orgId: string;
  conversationId?: string;
}) {
  const router = useRouter();

  useEffect(() => {
    const refresh = coalesce(() => router.refresh(), REFRESH_DEBOUNCE_MS);
    const stop = startRealtimeClient({
      apiBase: apiBaseUrl(),
      createSocket: (url) => new WebSocket(url) as SocketLike,
      fetchTicket: () => fetchRealtimeTicket(orgId),
      // An expired session: a server re-render sends the page through the session refresh.
      onUnauthorized: () => router.refresh(),
      onEvent: (event) => {
        if (isRelevant(event, conversationId)) refresh.call();
      },
      onReconnect: refresh.call,
    });
    return () => {
      stop();
      refresh.cancel();
    };
  }, [orgId, conversationId, router]);

  return null;
}
