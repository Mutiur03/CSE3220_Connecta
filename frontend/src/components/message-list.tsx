import Link from "next/link";
import type { Message } from "@/lib/inbox-api";
import { formatDateTime } from "@/lib/format";
import { readingOrder } from "@/lib/inbox-view";

type Props = {
  /** One page as the backend sends it: newest first. */
  messages: Message[];
  /** Link to the next page of older history, when there is one. */
  olderHref?: string;
  /** Link back toward newer history, when this is not the first page. */
  newerHref?: string;
};

const NO_TEXT = "Attachment or unsupported message";
const linkClass =
  "self-center text-sm text-secondary underline-offset-4 hover:text-primary hover:underline";

/** A thread in reading order. Customer messages sit left on the surface, ours right in the accent. */
export default function MessageList({ messages, olderHref, newerHref }: Props) {
  if (messages.length === 0) {
    return (
      <p className="rounded-control border border-line p-4 text-sm text-secondary">
        No messages in this conversation yet. New customer messages appear here
        as they arrive.
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-3">
      {olderHref && (
        <Link href={olderHref} rel="prev" className={linkClass}>
          Load older messages
        </Link>
      )}
      <ol aria-label="Messages" className="flex flex-col gap-3">
        {readingOrder(messages).map((message) => {
          const outbound = message.direction === "OUTBOUND";
          return (
            <li
              key={message.id}
              className={`flex max-w-[42rem] flex-col gap-1 ${
                outbound ? "items-end self-end" : "items-start self-start"
              }`}
            >
              <p
                className={`whitespace-pre-wrap break-words rounded-panel px-3 py-2 text-message ${
                  outbound
                    ? "rounded-br-sm bg-accent text-accent-ink"
                    : "rounded-bl-sm border border-line bg-surface"
                }`}
              >
                {message.text ?? NO_TEXT}
              </p>
              <p className="flex gap-2 text-caption text-secondary">
                {outbound && message.senderName && (
                  <span>{message.senderName}</span>
                )}
                <time dateTime={message.sentAt} className="figure">
                  {formatDateTime(message.sentAt)}
                </time>
              </p>
            </li>
          );
        })}
      </ol>
      {newerHref && (
        <Link href={newerHref} rel="next" className={linkClass}>
          Show newer messages
        </Link>
      )}
    </div>
  );
}
