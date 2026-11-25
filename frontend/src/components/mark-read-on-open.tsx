"use client";

import { useEffect } from "react";
import { markConversationReadAction } from "@/lib/inbox-actions";

type Props = { orgId: string; conversationId: string; unread: boolean };

/**
 * Renders nothing. While a conversation is open and reads as unread (on open, or when a new customer message
 * arrives during the visit), it marks it read. The server action revalidates the inbox, so the dot goes.
 */
export default function MarkReadOnOpen({
  orgId,
  conversationId,
  unread,
}: Props) {
  useEffect(() => {
    if (unread) void markConversationReadAction(orgId, conversationId);
  }, [orgId, conversationId, unread]);
  return null;
}
