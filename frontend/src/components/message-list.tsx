import Link from "next/link";
import type { Message } from "@/lib/inbox-api";
import { formatDateTime, formatTime } from "@/lib/format";
import { groupThread, readingOrder } from "@/lib/inbox-view";

type Props = {
  /** One page as the backend sends it: newest first. */
  messages: Message[];
  /** Day labels ("Today", "Yesterday") are relative to this, so the render is deterministic. */
  now: Date;
  /** Link to the next page of older history, when there is one. */
  olderHref?: string;
  /** Link back toward newer history, when this is not the first page. */
  newerHref?: string;
};

const NO_TEXT = "Attachment or unsupported message";
const linkClass =
  "self-center text-sm text-secondary underline-offset-4 hover:text-primary hover:underline";

/** 10px bubbles; the last bubble of a group squares the corner that points at its sender. */
const bubbleClass = (outbound: boolean, last: boolean) =>
  [
    "whitespace-pre-wrap break-words rounded-[10px] px-3 py-2 text-message",
    outbound
      ? "bg-accent text-accent-ink"
      : "border border-line bg-surface text-primary",
    last && (outbound ? "rounded-br-[4px]" : "rounded-bl-[4px]"),
  ]
    .filter(Boolean)
    .join(" ");

/**
 * A thread in reading order, split by day. Consecutive messages from one sender form a group with the time
 * shown once under it; every bubble keeps its full time as a tooltip. Customers sit left, the team right.
 */
export default function MessageList({
  messages,
  now,
  olderHref,
  newerHref,
}: Props) {
  if (messages.length === 0) {
    return (
      <p className="text-sm text-secondary">
        No messages in this conversation yet. New customer messages appear here
        as they arrive.
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-6">
      {olderHref && (
        <Link href={olderHref} rel="prev" className={linkClass}>
          Load older messages
        </Link>
      )}
      {groupThread(readingOrder(messages), now).map((day) => (
        <section
          key={day.key}
          aria-labelledby={`day-${day.key}`}
          className="flex flex-col gap-4"
        >
          <h3
            id={`day-${day.key}`}
            className="flex items-center gap-3 text-caption font-medium text-secondary before:h-px before:flex-1 before:bg-line after:h-px after:flex-1 after:bg-line"
          >
            {day.label}
          </h3>
          <ol
            aria-label={`Messages, ${day.label}`}
            className="flex flex-col gap-4"
          >
            {day.groups.map((group) => {
              const last = group.messages[group.messages.length - 1];
              return (
                <li
                  key={group.key}
                  className={`flex max-w-[85%] flex-col gap-0.5 ${
                    group.outbound
                      ? "items-end self-end"
                      : "items-start self-start"
                  }`}
                >
                  {group.messages.map((message) => (
                    <p
                      key={message.id}
                      title={formatDateTime(message.sentAt)}
                      className={bubbleClass(group.outbound, message === last)}
                    >
                      {message.text ?? NO_TEXT}
                    </p>
                  ))}
                  <p className="mt-1 flex gap-2 text-caption text-secondary">
                    {group.outbound && group.senderName && (
                      <span>{group.senderName}</span>
                    )}
                    <time dateTime={last.sentAt} className="figure">
                      {formatTime(last.sentAt)}
                    </time>
                  </p>
                </li>
              );
            })}
          </ol>
        </section>
      ))}
      {newerHref && (
        <Link href={newerHref} rel="next" className={linkClass}>
          Show newer messages
        </Link>
      )}
    </div>
  );
}
