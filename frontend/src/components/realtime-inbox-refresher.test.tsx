// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { REFRESH_DEBOUNCE_MS, type RealtimeEvent } from "@/lib/realtime";
import type { RealtimeSubscriber } from "@/lib/realtime-hub";
import RealtimeInboxRefresher from "./realtime-inbox-refresher";

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

const event = (conversationId: string): RealtimeEvent => ({
  type: "message.created",
  conversationId,
  messageId: "m1",
  channel: "MESSENGER",
  direction: "INBOUND",
  newConversation: false,
});

let root: Root;
let container: HTMLDivElement;

function mount(props: { orgId: string; conversationId?: string }) {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => root.render(<RealtimeInboxRefresher {...props} />));
  const [orgId, subscriber] = subscribeRealtime.mock.calls[0];
  expect(orgId).toBe(props.orgId);
  return subscriber;
}

beforeEach(() => {
  vi.useFakeTimers();
  refresh.mockReset();
  stop.mockReset();
  subscribeRealtime.mockClear();
});

afterEach(() => {
  vi.useRealTimers();
  container?.remove();
});

describe("RealtimeInboxRefresher", () => {
  it("refreshes once for a burst of events in the list", () => {
    const options = mount({ orgId: "org-1" });
    act(() => {
      options.onEvent?.(event("c1"));
      options.onEvent?.(event("c2"));
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("on a thread refreshes only for its own conversation", () => {
    const options = mount({ orgId: "org-1", conversationId: "c1" });
    act(() => {
      options.onEvent?.(event("other"));
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).not.toHaveBeenCalled();
    act(() => {
      options.onEvent?.(event("c1"));
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("refreshes after a reconnect, since events in between were not replayed", () => {
    const options = mount({ orgId: "org-1" });
    act(() => {
      options.onReconnect?.();
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("refreshes the route when the ticket route answers 401, so the session is renewed", () => {
    const options = mount({ orgId: "org-1" });
    expect(refresh).not.toHaveBeenCalled();
    act(() => options.onUnauthorized?.());
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("unsubscribes and drops a pending refresh on unmount", () => {
    const options = mount({ orgId: "org-1" });
    act(() => options.onEvent?.(event("c1")));
    act(() => root.unmount());
    expect(stop).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS));
    expect(refresh).not.toHaveBeenCalled();
  });
});
