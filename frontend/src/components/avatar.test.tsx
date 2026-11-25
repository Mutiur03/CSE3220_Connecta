// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import Avatar from "./avatar";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

const PIC = "https://cdn.example/a.jpg?sig=1";
let host: HTMLDivElement;
let root: Root;

const render = (props: Parameters<typeof Avatar>[0]) =>
  act(() => root.render(<Avatar {...props} />));

beforeEach(() => {
  host = document.createElement("div");
  document.body.append(host);
  root = createRoot(host);
});

afterEach(() => {
  act(() => root.unmount());
  host.remove();
});

describe("Avatar", () => {
  it("shows the initials when there is no picture", () => {
    render({ name: "Ana Silva", src: null });
    expect(host.querySelector("img")).toBeNull();
    expect(host.textContent).toBe("AS");
  });

  it("shows ? for an unknown name", () => {
    render({ name: null, src: null });
    expect(host.textContent).toBe("?");
  });

  it("renders a decorative, lazy, referrer-free image with a fixed size", () => {
    render({ name: "Ana Silva", src: PIC, size: "md" });
    const img = host.querySelector("img");
    expect(img?.getAttribute("src")).toBe(PIC);
    expect(img?.getAttribute("alt")).toBe("");
    expect(img?.getAttribute("loading")).toBe("lazy");
    expect(img?.getAttribute("referrerpolicy")).toBe("no-referrer");
    expect(img?.getAttribute("width")).toBe("48");
    expect(img?.getAttribute("height")).toBe("48");
  });

  it("is hidden from assistive tech because the name sits next to it", () => {
    render({ name: "Ana Silva", src: PIC });
    expect(host.firstElementChild?.getAttribute("aria-hidden")).toBe("true");
  });

  it("falls back to the initials when the picture fails to load", () => {
    render({ name: "Ana Silva", src: PIC });
    act(() => {
      host.querySelector("img")?.dispatchEvent(new Event("error"));
    });
    expect(host.querySelector("img")).toBeNull();
    expect(host.textContent).toBe("AS");
  });

  it("tries a refreshed picture link after the old one failed", () => {
    render({ name: "Ana Silva", src: PIC });
    act(() => {
      host.querySelector("img")?.dispatchEvent(new Event("error"));
    });
    render({ name: "Ana Silva", src: "https://cdn.example/b.jpg" });
    expect(host.querySelector("img")?.getAttribute("src")).toBe(
      "https://cdn.example/b.jpg",
    );
  });
});
