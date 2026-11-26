// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";
import AutoRefresh, { AUTO_REFRESH_MS } from "./auto-refresh";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

let container: HTMLDivElement;
let root: Root;
let state: DocumentVisibilityState;

beforeAll(() => {
  (
    globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
  ).IS_REACT_ACT_ENVIRONMENT = true;
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
});

beforeEach(() => {
  vi.useFakeTimers();
  state = "visible";
  refresh.mockClear();
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  act(() => root.render(<AutoRefresh />));
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.useRealTimers();
});

const advance = (ms: number) => act(() => void vi.advanceTimersByTime(ms));
const setVisibility = (next: DocumentVisibilityState) => {
  state = next;
  act(() => void document.dispatchEvent(new Event("visibilitychange")));
};

describe("AutoRefresh", () => {
  it("refreshes after the interval and not before", () => {
    advance(AUTO_REFRESH_MS - 1);
    expect(refresh).not.toHaveBeenCalled();
    advance(1);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("does not refresh while the tab is hidden", () => {
    setVisibility("hidden");
    advance(AUTO_REFRESH_MS * 3);
    expect(refresh).not.toHaveBeenCalled();
  });

  it("refreshes once when the tab becomes visible after a long absence", () => {
    setVisibility("hidden");
    advance(AUTO_REFRESH_MS * 2);
    setVisibility("visible");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("does not refresh on becoming visible within the interval", () => {
    setVisibility("hidden");
    advance(1000);
    setVisibility("visible");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("skips a tick while a save is in flight", () => {
    const busy = document.createElement("button");
    busy.setAttribute("aria-busy", "true");
    document.body.append(busy);
    advance(AUTO_REFRESH_MS);
    expect(refresh).not.toHaveBeenCalled();
    busy.remove();
    advance(AUTO_REFRESH_MS);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("stops the interval on unmount", () => {
    act(() => root.unmount());
    advance(AUTO_REFRESH_MS * 2);
    expect(refresh).not.toHaveBeenCalled();
    root = createRoot(container);
  });
});
