"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { apiBaseUrl } from "@/lib/api";
import {
  fetchRealtimeTicket,
  startRealtimeClient,
  type SocketLike,
} from "@/lib/realtime";

/**
 * Renders nothing. An open realtime socket is what makes a member Online, so this holds one for every page
 * of the organization. It ignores events: the inbox pages keep their own refresher.
 */
export default function PresenceConnection({ orgId }: { orgId: string }) {
  const router = useRouter();

  useEffect(
    () =>
      startRealtimeClient({
        apiBase: apiBaseUrl(),
        createSocket: (url) => new WebSocket(url) as SocketLike,
        fetchTicket: () => fetchRealtimeTicket(orgId),
        // An expired session: a server re-render sends the page through the session refresh.
        onUnauthorized: () => router.refresh(),
        onEvent: () => {},
        onReconnect: () => {},
      }),
    [orgId, router],
  );

  return null;
}
