"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";

type Props = {
  /** Where to remember the position. A different key (another tab, filter or page) starts at the top. */
  storageKey: string;
  className?: string;
  children: ReactNode;
};

const CURRENT_ROW = '[aria-current="page"]';

/** Puts the open row in the middle of the container, scrolling only the container, when it is not fully visible. */
function revealCurrent(box: HTMLElement) {
  const row = box.querySelector<HTMLElement>(CURRENT_ROW);
  if (!row) return;
  const rowRect = row.getBoundingClientRect();
  const boxRect = box.getBoundingClientRect();
  if (rowRect.top >= boxRect.top && rowRect.bottom <= boxRect.bottom) return;
  box.scrollTop +=
    rowRect.top - boxRect.top - (boxRect.height - rowRect.height) / 2;
}

/**
 * A scroll container that remembers where it was. The inbox list is rebuilt when an agent moves between the inbox
 * page and a thread, which would reset it to the top; the position is kept in sessionStorage for the tab. The open
 * row (aria-current="page") always ends up in view, placed before the first paint with no animation: after a
 * refresh, a link straight to a thread, or when the agent opens another row (click, J and K).
 */
export default function ScrollMemory({
  storageKey,
  className,
  children,
}: Props) {
  const box = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const element = box.current;
    if (!element) return;
    try {
      const stored = sessionStorage.getItem(storageKey);
      if (stored !== null && Number.isFinite(Number(stored))) {
        element.scrollTop = Number(stored);
      }
    } catch {
      // Storage can be blocked; the list then starts at the top.
    }
    revealCurrent(element);
  }, [storageKey]);

  return (
    <div
      ref={box}
      className={className}
      onScroll={(event) => {
        try {
          sessionStorage.setItem(
            storageKey,
            String(event.currentTarget.scrollTop),
          );
        } catch {
          // Not remembering is harmless.
        }
      }}
    >
      {children}
    </div>
  );
}
