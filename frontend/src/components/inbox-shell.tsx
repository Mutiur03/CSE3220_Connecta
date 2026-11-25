import type { ReactNode } from "react";

type Props = {
  /** The conversation list pane. */
  list: ReactNode;
  /** The thread pane, or the empty state when no conversation is open. */
  children: ReactNode;
  /** A conversation is open: below 1024px only the thread shows; without one only the list does. */
  threadOpen: boolean;
  /** The contact details pane, shown from 1280px beside an open thread. */
  details?: ReactNode;
};

/**
 * The inbox frame. From 1024px the list (340px) sits beside the thread, separated by a 1px border, each
 * scrolling on its own inside the viewport height (the app layout pins itself to it). Below that the list and
 * the thread are separate screens, scrolling the same way.
 * `data-inbox` tells the organization layout to drop its width and gutter for this page.
 */
export default function InboxShell({
  list,
  children,
  threadOpen,
  details,
}: Props) {
  return (
    <div data-inbox className="flex min-h-0 flex-1">
      <div
        className={`min-h-0 w-full flex-col border-line bg-surface lg:flex lg:w-[340px] lg:shrink-0 lg:border-r ${
          threadOpen ? "hidden" : "flex"
        }`}
      >
        {list}
      </div>
      <div
        className={`min-h-0 min-w-0 flex-1 flex-col bg-surface lg:flex ${
          threadOpen ? "flex" : "hidden"
        }`}
      >
        {children}
      </div>
      {details}
    </div>
  );
}
