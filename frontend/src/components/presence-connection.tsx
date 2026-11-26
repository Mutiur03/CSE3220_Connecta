"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { subscribeRealtime } from "@/lib/realtime-hub";

/**
 * Renders nothing. An open realtime socket is what makes a member Online, so this joins the
 * organization's shared socket for every page. It ignores events: the inbox pages listen themselves.
 */
export default function PresenceConnection({ orgId }: { orgId: string }) {
  const router = useRouter();

  useEffect(
    () =>
      subscribeRealtime(orgId, {
        // An expired session: a server re-render sends the page through the session refresh.
        onUnauthorized: () => router.refresh(),
      }),
    [orgId, router],
  );

  return null;
}
