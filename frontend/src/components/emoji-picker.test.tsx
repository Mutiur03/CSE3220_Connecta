// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it, vi } from "vitest";
import { EMOJI_COLUMNS, EMOJI_GROUPS } from "@/lib/emoji";
import EmojiPicker from "./emoji-picker";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

function mount() {
  const onPick = vi.fn();
  const onDismiss = vi.fn();
  const container = document.createElement("div");
  document.body.appendChild(container);
  act(() =>
    createRoot(container).render(
      <EmojiPicker onPick={onPick} onDismiss={onDismiss} />,
    ),
  );
  const popover = container.querySelector("[popover]") as HTMLElement;
  const cells = Array.from(popover.querySelectorAll("button"));
  return { container, popover, cells, onPick, onDismiss };
}

const press = (target: HTMLElement, key: string) =>
  act(() => {
    target.dispatchEvent(
      new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true }),
    );
  });

describe("emoji data", () => {
  it("has 80 to 100 unique emojis, each group filling whole grid rows", () => {
    const all = EMOJI_GROUPS.flatMap((group) => group.emojis);
    expect(all.length).toBeGreaterThanOrEqual(80);
    expect(all.length).toBeLessThanOrEqual(100);
    expect(new Set(all.map((emoji) => emoji.char)).size).toBe(all.length);
    EMOJI_GROUPS.forEach((group) =>
      expect(group.emojis.length % EMOJI_COLUMNS).toBe(0),
    );
  });
});

describe("EmojiPicker", () => {
  it("opens a popover from a named button, with labelled groups of named emojis", () => {
    const { container, popover, cells } = mount();
    const opener = container.querySelector(
      'button[aria-label="Insert emoji"]',
    ) as HTMLButtonElement;
    expect(opener.getAttribute("popovertarget")).toBe(popover.id);
    expect(popover.querySelectorAll('[role="group"]')).toHaveLength(
      EMOJI_GROUPS.length,
    );
    expect(popover.textContent).toContain("Gestures");
    expect(cells.every((cell) => cell.getAttribute("aria-label"))).toBe(true);
  });

  it("hands the chosen emoji to the caller", () => {
    const { cells, onPick } = mount();
    act(() => cells[0].click());
    expect(onPick).toHaveBeenCalledWith(EMOJI_GROUPS[0].emojis[0].char);
  });

  it("moves through the grid with arrow keys, Home and End", () => {
    const { cells, popover } = mount();
    // jsdom has no popover support and treats a closed one as unfocusable; stand in for an open one.
    popover.removeAttribute("popover");
    cells[0].focus();
    press(cells[0], "ArrowRight");
    expect(document.activeElement).toBe(cells[1]);
    press(cells[1], "ArrowDown");
    expect(document.activeElement).toBe(cells[1 + EMOJI_COLUMNS]);
    press(cells[1 + EMOJI_COLUMNS], "End");
    expect(document.activeElement).toBe(cells[cells.length - 1]);
    press(cells[cells.length - 1], "ArrowRight");
    expect(document.activeElement).toBe(cells[cells.length - 1]);
    press(cells[cells.length - 1], "Home");
    expect(document.activeElement).toBe(cells[0]);
  });

  it("closes on Escape and lets the caller take focus back", () => {
    const { cells, onDismiss } = mount();
    press(cells[0], "Escape");
    expect(onDismiss).toHaveBeenCalled();
  });
});
