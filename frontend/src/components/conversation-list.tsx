import Link from "next/link";
import type { Conversation } from "@/lib/inbox-api";
import { formatDateTime, formatRelative } from "@/lib/format";
import { INBOX_CHANNEL_LABELS, participantLabel } from "@/lib/inbox-view";
import { Badge } from "./ui/badge";

type Props = {
  orgId: string;
  conversations: Conversation[];
  /** Passed in so the relative times are deterministic. */
  now: Date;
};

const NO_MESSAGES = "No messages yet";

/** Conversations as linked rows, newest first. Channel is a small badge; rows are dividers, not cards. */
export default function ConversationList({ orgId, conversations, now }: Props) {
  return (
    <ul
      aria-label="Conversations"
      className="overflow-hidden rounded-panel border border-line bg-surface"
    >
      {conversations.map((conversation) => (
        <li
          key={conversation.id}
          className="border-b border-line-subtle last:border-b-0"
        >
          <Link
            href={`/organizations/${orgId}/inbox/${conversation.id}`}
            className="flex flex-col gap-1 px-4 py-3 transition-colors hover:bg-hover/60 sm:flex-row sm:items-center sm:gap-4"
          >
            <span className="flex min-w-0 flex-1 flex-col gap-0.5">
              <span className="truncate font-medium">
                {participantLabel(conversation.participantName)}
              </span>
              <span className="truncate text-sm text-secondary">
                {conversation.lastMessagePreview ?? NO_MESSAGES}
              </span>
            </span>
            <span className="flex shrink-0 items-center gap-3 text-sm text-secondary sm:justify-end">
              <Badge>{INBOX_CHANNEL_LABELS[conversation.channel]}</Badge>
              {conversation.lastMessageAt && (
                <time
                  dateTime={conversation.lastMessageAt}
                  title={formatDateTime(conversation.lastMessageAt)}
                  className="figure w-24 sm:text-right"
                >
                  {formatRelative(conversation.lastMessageAt, now)}
                </time>
              )}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
