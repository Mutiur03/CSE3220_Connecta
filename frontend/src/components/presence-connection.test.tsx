// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { RealtimeSubscriber } from "@/lib/realtime-hub";
import PresenceConnection from "./presence-connection";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

const stop = vi.fn();
const subscribeRealtime = vi.fn<
  (orgId: string, subscriber: RealtimeSubscriber) => () => void
>(() => stop);
vi.mock("@/lib/realtime-hub", () => ({
  subscribeRealtime: (orgId: string, subscriber: RealtimeSubscriber) =>
    subscribeRealtime(orgId, subscriber),
}));

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root;
let container: HTMLDivElement;

const mount = (orgId: string) => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => root.render(<PresenceConnection orgId={orgId} />));
};

beforeEach(() => {
  refresh.mockReset();
  stop.mockReset();
  subscribeRealtime.mockClear();
});

afterEach(() => {
  container?.remove();
});

describe("PresenceConnection", () => {
  it("renders nothing and joins the shared socket of its organization", () => {
    mount("org-1");
    expect(container.innerHTML).toBe("");
    expect(subscribeRealtime).toHaveBeenCalledTimes(1);
    expect(subscribeRealtime.mock.calls[0][0]).toBe("org-1");
  });

  it("does not listen to events or reconnects, so it never refreshes the page for them", () => {
    mount("org-1");
    const subscriber = subscribeRealtime.mock.calls[0][1];
    expect(subscriber.onEvent).toBeUndefined();
    expect(subscriber.onReconnect).toBeUndefined();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("asks the server to renew an expired session and unsubscribes on unmount", () => {
    mount("org-1");
    subscribeRealtime.mock.calls[0][1].onUnauthorized?.();
    expect(refresh).toHaveBeenCalledTimes(1);
    act(() => root.unmount());
    expect(stop).toHaveBeenCalledTimes(1);
  });
});
