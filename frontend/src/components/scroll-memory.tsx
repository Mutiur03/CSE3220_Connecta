"use client";

import { useLayoutEffect, useRef, type ReactNode } from "react";

type Props = {
  /** Where to remember the position. A different key (another tab, filter or page) starts at the top. */
  storageKey: string;
  className?: string;
  children: ReactNode;
};

const CURRENT_ROW = '[aria-current="page"]';

/**
 * The same placement as the layout effect below, as a script in the server HTML. The browser runs it while it
 * parses the page, so the list is already in place at the first paint; an effect only runs after hydration, which
 * leaves the list at the top for as long as the scripts take to load. It sits in innerHTML, which the browser
 * runs when it parses the page and ignores when React renders the component in the browser, where the effect
 * does the work. The storage key is read from data-scroll-key, so nothing is built into the script text. It is
 * rendered by ScrollPlacement after everything else in the pane, so the list has its final height when it runs.
 */
const PLACE_BEFORE_PAINT = `(function(){var b=document.currentScript.parentElement.parentElement.querySelector('[data-scroll-key]');if(!b)return;try{var v=sessionStorage.getItem(b.dataset.scrollKey);if(v!==null&&isFinite(+v))b.scrollTop=+v}catch(e){}var r=b.querySelector('${CURRENT_ROW}');if(r){var a=r.getBoundingClientRect(),c=b.getBoundingClientRect();if(a.top<c.top||a.bottom>c.bottom)b.scrollTop+=a.top-c.top-(c.height-a.height)/2}})()`;

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
      data-scroll-key={storageKey}
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

/**
 * Render this as the last element of the pane that holds a ScrollMemory, so the placement script runs once the
 * pane has its final layout. It draws nothing.
 */
export function ScrollPlacement() {
  return (
    <div
      hidden
      dangerouslySetInnerHTML={{
        __html: `<script>${PLACE_BEFORE_PAINT}</script>`,
      }}
    />
  );
}
