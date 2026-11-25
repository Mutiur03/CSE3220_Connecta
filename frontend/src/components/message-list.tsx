import Link from "next/link";
import type { Attachment, Message } from "@/lib/inbox-api";
import { formatDateTime, formatTime } from "@/lib/format";
import { groupThread, readingOrder } from "@/lib/inbox-view";
import { FileIcon, InstagramIcon } from "./icons";
import MessageMedia from "./message-media";

type Props = {
  /** One page as the backend sends it: newest first. */
  messages: Message[];
  /** Day labels ("Today", "Yesterday") are relative to this, so the render is deterministic. */
  now: Date;
  /** Link to the next page of older history, when there is one. */
  olderHref?: string;
  /** Link back toward newer history, when this is not the first page. */
  newerHref?: string;
  /** The messaging app the thread comes from ("Instagram", "Messenger"), for the note on content we cannot show. */
  appName?: string;
};

const NO_TEXT = "Attachment or unsupported message";
/** The text the backend stores for a message that is only media; the media itself replaces it. */
/** The stand-in for a view-once photo, which is never kept or shown. */
const DISAPPEARING_PHOTO = "[Disappearing photo]";
const MEDIA_LABELS = new Set([
  "[Photo]",
  "[Video]",
  "[Voice message]",
  "[Attachment]",
  "[Shared post]",
  "[Story mention]",
  DISAPPEARING_PHOTO,
]);
const linkClass =
  "self-center text-sm text-secondary underline-offset-4 hover:text-primary hover:underline";

/** 10px bubbles; the last bubble of a group squares the corner that points at its sender. */
const bubbleClass = (outbound: boolean, last: boolean) =>
  [
    "flex flex-col gap-2 whitespace-pre-wrap break-words rounded-[10px] px-3 py-2 text-message",
    outbound
      ? "bg-accent text-accent-ink"
      : "border border-line bg-surface text-primary",
    last && (outbound ? "rounded-br-[4px]" : "rounded-bl-[4px]"),
  ]
    .filter(Boolean)
    .join(" ");

/** The message text, unless it is only the stand-in label for media that is shown. */
function caption({ text, attachments }: Message, appName?: string) {
  if (attachments.length === 0) {
    // Only a stand-in label and nothing to show: the platform sent the message but handed us no content for it
    // (some media types are not shared with apps). Say that, and where to see it, instead of a bracket label that
    // reads like a failed load. Inherits the bubble colour with reduced opacity, so it works on both bubbles.
    if (text === DISAPPEARING_PHOTO) {
      return (
        <span className="italic opacity-75">
          Disappearing photo. It isn’t kept or shown here.
        </span>
      );
    }
    if (text && MEDIA_LABELS.has(text)) {
      return (
        <span className="italic opacity-75">
          Can’t show this attachment here. Open it in {appName ?? "the app"}.
        </span>
      );
    }
    return text ?? NO_TEXT;
  }
  return text && !MEDIA_LABELS.has(text) ? text : null;
}

/** The host of an http(s) link, or null for anything else (never made into a link). */
function webHost(url: string) {
  try {
    const { protocol, host } = new URL(url);
    return protocol === "https:" || protocol === "http:" ? host : null;
  } catch {
    return null;
  }
}

/**
 * A shared post or story: one link with the Instagram glyph, its title and host. `currentColor` borders,
 * hover tint and focus ring keep it legible on both the surface and the accent bubble.
 */
function LinkCard({ attachment }: { attachment: Attachment }) {
  const title = attachment.fileName ?? "Instagram post";
  const host = webHost(attachment.url);
  const body = (
    <>
      <InstagramIcon />
      <span className="flex min-w-0 flex-col">
        <span className="truncate font-medium">{title}</span>
        {host && <span className="truncate text-caption">{host}</span>}
      </span>
    </>
  );
  const cardClass =
    "flex w-64 max-w-full items-center gap-2 rounded-[6px] border border-current/20 px-3 py-2 whitespace-normal [&_svg]:size-4 [&_svg]:shrink-0";
  if (!host) return <div className={cardClass}>{body}</div>;
  return (
    <a
      href={attachment.url}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={`${title} on Instagram (opens in a new tab)`}
      className={`${cardClass} transition-colors duration-150 ease-out hover:bg-current/10 focus-visible:outline-current motion-reduce:transition-none pointer-coarse:min-h-11`}
    >
      {body}
    </a>
  );
}

function AttachmentView({
  attachment,
  sender,
}: {
  attachment: Attachment;
  sender: string;
}) {
  switch (attachment.kind) {
    case "IMAGE":
    case "VIDEO":
      return (
        <MessageMedia
          attachment={attachment}
          alt={attachment.fileName ?? `Photo from ${sender}`}
        />
      );
    case "AUDIO":
      return (
        <audio
          src={attachment.url}
          controls
          preload="metadata"
          aria-label={`Voice message from ${sender}`}
          className="w-64 max-w-full"
        />
      );
    case "LINK":
      return <LinkCard attachment={attachment} />;
    default:
      return (
        <a
          href={attachment.url}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 underline underline-offset-4 [&_svg]:size-4 [&_svg]:shrink-0"
        >
          <FileIcon />
          <span className="min-w-0 break-all">
            {attachment.fileName ?? "Attachment"}
          </span>
        </a>
      );
  }
}

/**
 * A thread in reading order, split by day. Consecutive messages from one sender form a group with the time
 * shown once under it; every bubble keeps its full time as a tooltip. Customers sit left, the team right.
 */
export default function MessageList({
  messages,
  now,
  olderHref,
  newerHref,
  appName,
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
                    <div
                      key={message.id}
                      title={formatDateTime(message.sentAt)}
                      className={bubbleClass(group.outbound, message === last)}
                    >
                      {message.attachments.map((attachment) => (
                        <AttachmentView
                          key={attachment.id}
                          attachment={attachment}
                          sender={
                            message.senderName ??
                            (group.outbound ? "the team" : "the customer")
                          }
                        />
                      ))}
                      {caption(message, appName)}
                    </div>
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
