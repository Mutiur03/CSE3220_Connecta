"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";

type Props = {
  /** Where to remember the position. A different key (another tab, filter or page) starts at the top. */
  storageKey: string;
  className?: string;
  children: ReactNode;
};

const CURRENT_ROW = '[aria-current="page"]';

const prefersReducedMotion = () =>
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/**
 * Scrolls the container, and only it, so the open row sits in the middle when it is outside the visible part.
 * Smooth when the list came back from a remembered position (the agent moved to another row); instant when it
 * starts cold (a refresh, a link), so the first paint already shows the row.
 */
function revealCurrent(box: HTMLElement, smooth: boolean) {
  const row = box.querySelector<HTMLElement>(CURRENT_ROW);
  if (!row) return;
  const rowRect = row.getBoundingClientRect();
  const boxRect = box.getBoundingClientRect();
  if (rowRect.top >= boxRect.top && rowRect.bottom <= boxRect.bottom) return;
  const top =
    box.scrollTop +
    rowRect.top -
    boxRect.top -
    (boxRect.height - rowRect.height) / 2;
  if (smooth && !prefersReducedMotion() && typeof box.scrollTo === "function") {
    box.scrollTo({ top, behavior: "smooth" });
  } else {
    box.scrollTop = top;
  }
}

/**
 * A scroll container that remembers where it was. The inbox list is rebuilt when an agent moves between the inbox
 * page and a thread, which would reset it to the top; the position is kept in sessionStorage for the tab. The open
 * row (aria-current="page") always ends up in view: placed before the first paint after a refresh or a link straight
 * to a thread, and a smooth glide from the remembered position when the agent opens another row (click, J and K).
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
    let remembered = false;
    try {
      const stored = sessionStorage.getItem(storageKey);
      if (stored !== null && Number.isFinite(Number(stored))) {
        element.scrollTop = Number(stored);
        remembered = true;
      }
    } catch {
      // Storage can be blocked; the list then starts at the top.
    }
    revealCurrent(element, remembered);
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
