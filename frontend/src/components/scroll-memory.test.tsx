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

  it("saves the position as the list scrolls", () => {
    const { box } = mount("list:c");
    act(() => {
      box.scrollTop = 64;
      box.dispatchEvent(new Event("scroll", { bubbles: true }));
    });
    expect(sessionStorage.getItem("list:c")).toBe("64");
  });
});
