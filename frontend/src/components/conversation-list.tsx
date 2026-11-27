import Link from "next/link";
import type { Conversation } from "@/lib/inbox-api";
import { formatRelative } from "@/lib/format";
import { LocalTime } from "./local-time";
import {
  INBOX_CHANNEL_LABELS,
  heldByOffline,
  participantLabel,
  threadHref,
  type InboxChannel,
  type InboxFilters,
} from "@/lib/inbox-view";
import Avatar from "./avatar";
import { FacebookIcon, InstagramIcon } from "./icons";

type Props = {
  orgId: string;
  conversations: Conversation[];
  /** Passed in so the relative times are deterministic. */
  now: Date;
  /** The open conversation, marked as the current row. */
  selectedId?: string;
  /** Carried into each row link, so opening a thread keeps the list as it is. */
  filters?: InboxFilters;
  listPage?: number;
};

const UNREAD_DOT = "size-1.5 shrink-0 rounded-full bg-highlight";

const NO_MESSAGES = "No messages yet";

const CHANNEL_GLYPHS: Record<InboxChannel, () => React.JSX.Element> = {
  INSTAGRAM: InstagramIcon,
  MESSENGER: FacebookIcon,
};

/**
 * Conversations as 64px linked rows, newest first: avatar with a small channel glyph on its corner, name and
 * time on one line, a one-line preview below. The open row takes the selected surface and a 2px accent bar.
 */
export default function ConversationList({
  orgId,
  conversations,
  now,
  selectedId,
  filters = {},
  listPage = 0,
}: Props) {
  return (
    <ul aria-label="Conversations">
      {conversations.map((conversation) => {
        const Glyph = CHANNEL_GLYPHS[conversation.channel];
        const channel = INBOX_CHANNEL_LABELS[conversation.channel];
        return (
          <li
            key={conversation.id}
            className="border-b border-line-subtle last:border-b-0"
          >
            <Link
              href={threadHref(
                `/organizations/${orgId}/inbox/${conversation.id}`,
                filters,
                listPage,
              )}
              // The list keeps its place when a thread opens; Next would otherwise scroll it to the top.
              scroll={false}
              aria-current={conversation.id === selectedId ? "page" : undefined}
              className="relative flex h-16 items-center gap-3 px-4 transition-colors duration-150 ease-out hover:bg-hover motion-reduce:transition-none aria-[current=page]:bg-selected aria-[current=page]:before:absolute aria-[current=page]:before:inset-y-0 aria-[current=page]:before:left-0 aria-[current=page]:before:w-0.5 aria-[current=page]:before:bg-accent"
            >
              <span className="relative shrink-0">
                <Avatar
                  name={conversation.participantName}
                  src={conversation.avatarUrl}
                />
                <span
                  title={channel}
                  className="absolute -right-1 -bottom-1 grid size-4.5 place-items-center rounded-full bg-surface text-secondary ring-1 ring-line [&_svg]:size-3"
                >
                  <Glyph />
                  <span className="sr-only">{channel}</span>
                </span>
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="flex items-baseline gap-2">
                  <span
                    className={`min-w-0 flex-1 truncate ${
                      conversation.unread ? "font-semibold" : "font-medium"
                    }`}
                  >
                    {participantLabel(conversation.participantName)}
                  </span>
                  {conversation.unread && (
                    <span className="flex items-center self-center">
                      <span aria-hidden="true" className={UNREAD_DOT} />
                      <span className="sr-only">Unread</span>
                    </span>
                  )}
                  {conversation.lastMessageAt && (
                    <LocalTime
                      iso={conversation.lastMessageAt}
                      titleKind="dateTime"
                      className="figure shrink-0 text-caption text-secondary"
                    >
                      {formatRelative(conversation.lastMessageAt, now)}
                    </LocalTime>
                  )}
                </span>
                <span className="flex items-baseline gap-2">
                  <span
                    className={`min-w-0 flex-1 truncate text-sm ${
                      conversation.unread ? "text-primary" : "text-secondary"
                    }`}
                  >
                    {conversation.lastMessagePreview ?? NO_MESSAGES}
                  </span>
                  {conversation.assignee && (
                    <span className="flex max-w-[40%] shrink-0 items-baseline text-caption text-secondary">
                      <span className="sr-only">Assigned to </span>
                      <span className="truncate">
                        {conversation.assignee.name}
                      </span>
                      {heldByOffline(conversation) && (
                        <span className="shrink-0">{" · offline"}</span>
                      )}
                    </span>
                  )}
                  {filters.tab === "ALL" &&
                    conversation.status === "CLOSED" && (
                      <span className="shrink-0 text-caption text-secondary">
                        Closed
                      </span>
                    )}
                </span>
              </span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}
