"use client";

import { useSyncExternalStore, type ComponentProps } from "react";
import { formatDate, formatDateTime, formatTime } from "@/lib/format";

/** How much of a moment to show: the day, the day and the clock, or the clock alone. */
export type TimeKind = "date" | "dateTime" | "time";

const FORMATTERS = {
  date: formatDate,
  dateTime: formatDateTime,
  time: formatTime,
} as const;

const SERVER_TIME_ZONE = "UTC";

const subscribeToNothing = () => () => {};

/**
 * The text of a moment. In the browser it is in the reader's own time zone; while rendering on the server (and
 * while hydrating) it is in UTC, because the server cannot know the reader's zone. Reading the browser through
 * useSyncExternalStore lets React swap the text in after hydration without a mismatch warning.
 */
function useMomentText(iso: string, kind: TimeKind | undefined): string {
  return useSyncExternalStore(
    subscribeToNothing,
    () => (kind ? FORMATTERS[kind](iso) : ""),
    () => (kind ? FORMATTERS[kind](iso, SERVER_TIME_ZONE) : ""),
  );
}

type LocalTimeProps = {
  iso: string;
  /** What to print. Left out, the children are printed (for wording such as "5 minutes ago"). */
  kind?: TimeKind;
  /** A hover tooltip with the moment in local time. */
  titleKind?: TimeKind;
} & Omit<ComponentProps<"time">, "dateTime" | "title">;

/** A `<time>` that shows an ISO timestamp, and optionally a tooltip for it, in the reader's local time. */
export function LocalTime({
  iso,
  kind,
  titleKind,
  children,
  ...rest
}: LocalTimeProps) {
  const text = useMomentText(iso, kind);
  const title = useMomentText(iso, titleKind);
  return (
    <time dateTime={iso} title={titleKind ? title : undefined} {...rest}>
      {kind ? text : children}
    </time>
  );
}

type LocalTitledProps = { iso: string; kind?: TimeKind } & Omit<
  ComponentProps<"div">,
  "title"
>;

/** A `<div>` whose hover tooltip is a moment in the reader's local time (an attribute cannot come from LocalTime). */
export function LocalTitled({
  iso,
  kind = "dateTime",
  ...rest
}: LocalTitledProps) {
  const title = useMomentText(iso, kind);
  return <div title={title} {...rest} />;
}
