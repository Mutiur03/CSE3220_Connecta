// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import ScrollMemory from "./scroll-memory";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

afterEach(() => {
  sessionStorage.clear();
  document.body.innerHTML = "";
});

function mount(storageKey: string) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() =>
    root.render(
      <ScrollMemory storageKey={storageKey}>
        <p>rows</p>
      </ScrollMemory>,
    ),
  );
  return { box: container.firstElementChild as HTMLDivElement, root };
}

describe("ScrollMemory", () => {
  it("restores the saved position when it mounts", () => {
    sessionStorage.setItem("list:a", "120");
    const { box } = mount("list:a");
    expect(box.scrollTop).toBe(120);
  });

  it("starts at the top for a key it has not seen", () => {
    sessionStorage.setItem("list:a", "120");
    const { box } = mount("list:b");
    expect(box.scrollTop).toBe(0);
  });

  it("brings the open row into view when it is outside the visible part", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    const rect = (top: number, height: number) =>
      ({ top, bottom: top + height, height }) as DOMRect;
    const original = HTMLElement.prototype.getBoundingClientRect;
    HTMLElement.prototype.getBoundingClientRect = function () {
      if (this.getAttribute("aria-current") === "page") return rect(500, 64);
      return rect(0, 200);
    };
    try {
      act(() =>
        root.render(
          <ScrollMemory storageKey="list:d">
            <a aria-current="page" href="/x">
              open
            </a>
          </ScrollMemory>,
        ),
      );
      // Row top 500, box 0..200 (height 200): centre it, so scroll by 500 - (200 - 64) / 2.
      expect((container.firstElementChild as HTMLDivElement).scrollTop).toBe(
        432,
      );
    } finally {
      HTMLElement.prototype.getBoundingClientRect = original;
    }
  });

  it("saves the position as the list scrolls", () => {
    const { box } = mount("list:c");
    act(() => {
      box.scrollTop = 64;
      box.dispatchEvent(new Event("scroll", { bubbles: true }));
    });
    expect(sessionStorage.getItem("list:c")).toBe("64");
  });
});
