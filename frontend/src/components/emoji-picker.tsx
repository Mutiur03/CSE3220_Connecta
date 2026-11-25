"use client";

import { useEffect, useId, useRef, type KeyboardEvent } from "react";
import { EMOJI_COLUMNS, EMOJI_GROUPS } from "@/lib/emoji";
import { SmileIcon } from "./icons";

type Props = {
  /** Called with the chosen emoji; the picker has already closed. */
  onPick: (emoji: string) => void;
  /** Called when Escape closes the picker, so the caller can take focus back. */
  onDismiss: () => void;
};

const STEP: Record<string, number> = {
  ArrowRight: 1,
  ArrowLeft: -1,
  ArrowDown: EMOJI_COLUMNS,
  ArrowUp: -EMOJI_COLUMNS,
};

const CELL =
  "grid size-8 place-items-center rounded-lg text-lg leading-none transition-colors duration-150 ease-out hover:bg-hover focus-visible:bg-hover motion-reduce:transition-none pointer-coarse:size-11";

/**
 * An emoji button and its picker: a native popover (top layer, light dismiss, Escape) holding a short grid
 * of common emojis in labelled groups. Opening focuses the first emoji; arrow keys, Home and End move
 * through the grid.
 */
export default function EmojiPicker({ onPick, onDismiss }: Props) {
  const id = useId();
  const popoverRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const popover = popoverRef.current;
    if (!popover) return;
    const focusFirst = (event: Event) => {
      if ((event as ToggleEvent).newState === "open")
        popover.querySelector("button")?.focus();
    };
    popover.addEventListener("toggle", focusFirst);
    return () => popover.removeEventListener("toggle", focusFirst);
  }, []);

  const close = () => popoverRef.current?.hidePopover?.();

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      close();
      onDismiss();
      return;
    }
    const buttons = Array.from(event.currentTarget.querySelectorAll("button"));
    const at = buttons.indexOf(event.target as HTMLButtonElement);
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? buttons.length - 1
          : at + (STEP[event.key] ?? NaN);
    const target = buttons[next];
    if (at < 0 || !target) return;
    event.preventDefault();
    target.focus();
  };

  return (
    <>
      <button
        type="button"
        popoverTarget={id}
        aria-label="Insert emoji"
        title="Insert emoji"
        className="emoji-anchor grid size-8 shrink-0 place-items-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary disabled:pointer-events-none disabled:opacity-50 motion-reduce:transition-none pointer-coarse:size-11 [&_svg]:size-4.5"
      >
        <SmileIcon />
      </button>
      <div
        ref={popoverRef}
        id={id}
        popover="auto"
        role="dialog"
        aria-label="Emoji"
        onKeyDown={onKeyDown}
        className="emoji-picker max-h-72 w-max max-w-[calc(100vw-1rem)] overflow-y-auto overscroll-contain p-2"
      >
        {EMOJI_GROUPS.map((group) => (
          <div
            key={group.label}
            role="group"
            aria-labelledby={`${id}-${group.label}`}
            className="not-first:mt-2"
          >
            <p
              id={`${id}-${group.label}`}
              className="px-1 pb-1 text-caption font-medium text-secondary"
            >
              {group.label}
            </p>
            <div className="grid grid-cols-8">
              {group.emojis.map((emoji) => (
                <button
                  key={emoji.char}
                  type="button"
                  aria-label={emoji.name}
                  title={emoji.name}
                  className={CELL}
                  onClick={() => {
                    close();
                    onPick(emoji.char);
                  }}
                >
                  {emoji.char}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
