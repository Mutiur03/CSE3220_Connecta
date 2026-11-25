"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";

type Props = {
  /** Where to remember the position. A different key (another tab, filter or page) starts at the top. */
  storageKey: string;
  className?: string;
  children: ReactNode;
};

/**
 * A scroll container that remembers where it was. The inbox list is rebuilt when an agent moves between the inbox
 * page and a thread, which would reset it to the top; the position is kept in sessionStorage for the tab.
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
      const saved = Number(sessionStorage.getItem(storageKey));
      if (Number.isFinite(saved) && saved > 0) element.scrollTop = saved;
    } catch {
      // Storage can be blocked; the list then starts at the top.
    }
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
