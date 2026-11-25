"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";
import {
  INBOX_SHORTCUTS,
  isTypingTarget,
  neighbourIndex,
  shortcutFor,
  type InboxShortcutAction,
} from "@/lib/inbox-keys";
import { CloseIcon, KeyboardIcon } from "./icons";

/** Hooks the page exposes; kept as ids and data attributes so the server components stay server components. */
const ROWS = 'ul[aria-label="Conversations"] a';
const STATUS_BUTTON = '[data-shortcut="status"] button';
const REPLY_BOX = "reply-text";
const SEARCH_BOX = "inbox-search";

const ICON_BUTTON =
  "inline-flex size-8 items-center justify-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary motion-reduce:transition-none pointer-coarse:size-11 [&_svg]:size-4";

/**
 * Keyboard shortcuts for the inbox, plus the button and native dialog that list them. J and K move between
 * rows, E presses the thread's Close or Reopen button, R and / focus the reply and search boxes, ? opens the sheet.
 */
export default function InboxKeyboard() {
  const router = useRouter();
  const sheet = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const run = (action: InboxShortcutAction) => {
      if (action === "next" || action === "previous") {
        const rows = [...document.querySelectorAll<HTMLAnchorElement>(ROWS)];
        const current = rows.findIndex(
          (row) => row.getAttribute("aria-current") === "page",
        );
        const index = neighbourIndex(
          rows.length,
          current,
          action === "next" ? 1 : -1,
        );
        if (index === null) return false;
        router.push(rows[index].getAttribute("href") ?? "");
        rows[index].scrollIntoView({ block: "nearest" });
        return true;
      }
      if (action === "toggleStatus") {
        const button = document.querySelector<HTMLButtonElement>(STATUS_BUTTON);
        button?.click();
        return Boolean(button);
      }
      if (action === "help") {
        sheet.current?.showModal();
        return true;
      }
      const box = document.getElementById(
        action === "reply" ? REPLY_BOX : SEARCH_BOX,
      );
      box?.focus();
      return Boolean(box);
    };

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || isTypingTarget(event.target)) return;
      if (sheet.current?.open) return;
      const action = shortcutFor(event);
      // Only a shortcut that did something takes the key, so "/" still types elsewhere when there is no search box.
      if (action && run(action)) event.preventDefault();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [router]);

  return (
    <>
      <button
        type="button"
        aria-label="Keyboard shortcuts"
        aria-keyshortcuts="Shift+?"
        title="Keyboard shortcuts (?)"
        onClick={() => sheet.current?.showModal()}
        className={ICON_BUTTON}
      >
        <KeyboardIcon />
      </button>
      <dialog
        ref={sheet}
        aria-labelledby="shortcuts-title"
        className="m-auto w-[min(22rem,calc(100vw-2rem))] rounded-[12px] border border-line bg-surface p-0 text-primary shadow-lg backdrop:bg-black/40"
        onClick={(event) => {
          // A click on the backdrop lands on the dialog itself.
          if (event.target === event.currentTarget) sheet.current?.close();
        }}
      >
        <div className="flex items-center justify-between border-b border-line px-4 py-3">
          <h2 id="shortcuts-title" className="text-title font-semibold">
            Keyboard shortcuts
          </h2>
          <form method="dialog">
            <button type="submit" aria-label="Close" className={ICON_BUTTON}>
              <CloseIcon />
            </button>
          </form>
        </div>
        <dl className="flex flex-col px-4 py-2">
          {INBOX_SHORTCUTS.map((shortcut) => (
            <div
              key={shortcut.key}
              className="flex items-center justify-between gap-4 border-b border-line-subtle py-2 last:border-b-0"
            >
              <dt className="text-sm">{shortcut.label}</dt>
              <dd>
                <kbd className="inline-grid min-w-6 place-items-center rounded-[6px] border border-line bg-app px-1.5 py-0.5 font-sans text-caption font-medium text-secondary">
                  {shortcut.key}
                </kbd>
              </dd>
            </div>
          ))}
        </dl>
      </dialog>
    </>
  );
}
