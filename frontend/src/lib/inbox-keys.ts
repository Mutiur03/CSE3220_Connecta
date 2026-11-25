/** Inbox keyboard shortcuts from docs/design-brief.md. */
export const INBOX_SHORTCUTS = [
  { key: "J", action: "next", label: "Next conversation" },
  { key: "K", action: "previous", label: "Previous conversation" },
  { key: "E", action: "toggleStatus", label: "Close or reopen" },
  { key: "R", action: "reply", label: "Write a reply" },
  { key: "/", action: "search", label: "Search conversations" },
  { key: "?", action: "help", label: "Show these shortcuts" },
] as const;

export type InboxShortcutAction = (typeof INBOX_SHORTCUTS)[number]["action"];

type KeyLike = {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  altKey: boolean;
};

/** The action a key press asks for, or null. Presses with Ctrl, Cmd or Alt belong to the browser. */
export function shortcutFor(event: KeyLike): InboxShortcutAction | null {
  if (event.ctrlKey || event.metaKey || event.altKey) return null;
  const key = event.key.length === 1 ? event.key.toUpperCase() : event.key;
  return (
    INBOX_SHORTCUTS.find((shortcut) => shortcut.key === key)?.action ?? null
  );
}

const TYPING_TAGS = new Set(["INPUT", "TEXTAREA", "SELECT"]);

/** Single-letter shortcuts never fire while someone types in a field. */
export const isTypingTarget = (target: EventTarget | null) =>
  target instanceof HTMLElement &&
  (TYPING_TAGS.has(target.tagName) || target.isContentEditable === true);

/**
 * The row index J (+1) or K (-1) moves to. With no open row J starts at the top and K at the bottom; the ends
 * stay put rather than wrap, so a long press never jumps across the list.
 */
export function neighbourIndex(
  count: number,
  current: number,
  step: 1 | -1,
): number | null {
  if (count === 0) return null;
  if (current < 0) return step === 1 ? 0 : count - 1;
  const next = current + step;
  return next >= 0 && next < count ? next : null;
}
