// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  REFRESH_DEBOUNCE_MS,
  type RealtimeClientOptions,
  type RealtimeEvent,
} from "@/lib/realtime";
import RealtimeInboxRefresher from "./realtime-inbox-refresher";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

const stop = vi.fn();
const startRealtimeClient = vi.fn<
  (options: RealtimeClientOptions) => () => void
>(() => stop);
vi.mock("@/lib/realtime", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/realtime")>()),
  startRealtimeClient: (options: RealtimeClientOptions) =>
    startRealtimeClient(options),
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
  return startRealtimeClient.mock.calls[0][0];
}

beforeEach(() => {
  vi.useFakeTimers();
  refresh.mockReset();
  stop.mockReset();
  startRealtimeClient.mockClear();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  container?.remove();
});

describe("RealtimeInboxRefresher", () => {
  it("asks for a ticket for the organization and refreshes once for a burst in the list", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ticket: "t1" }),
    });
    vi.stubGlobal("fetch", fetchMock);
    const options = mount({ orgId: "org-1" });
    expect(await options.fetchTicket()).toBe("t1");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/organizations/org-1/realtime/tickets",
      { method: "POST" },
    );
    act(() => {
      options.onEvent(event("c1"));
      options.onEvent(event("c2"));
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("on a thread refreshes only for its own conversation", () => {
    const options = mount({ orgId: "org-1", conversationId: "c1" });
    act(() => {
      options.onEvent(event("other"));
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).not.toHaveBeenCalled();
    act(() => {
      options.onEvent(event("c1"));
      vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS);
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("stops the client and drops a pending refresh on unmount", () => {
    const options = mount({ orgId: "org-1" });
    act(() => options.onEvent(event("c1")));
    act(() => root.unmount());
    expect(stop).toHaveBeenCalledTimes(1);
    act(() => vi.advanceTimersByTime(REFRESH_DEBOUNCE_MS));
    expect(refresh).not.toHaveBeenCalled();
  });
});
