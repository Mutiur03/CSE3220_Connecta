import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { RealtimeClientOptions, RealtimeEvent } from "./realtime";
import { subscribeRealtime } from "./realtime-hub";

const clients: {
  options: RealtimeClientOptions;
  stop: ReturnType<typeof vi.fn>;
}[] = [];
vi.mock("@/lib/api", () => ({ apiBaseUrl: () => "http://api" }));
vi.mock("@/lib/realtime", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./realtime")>()),
  startRealtimeClient: (options: RealtimeClientOptions) => {
    const stop = vi.fn();
    clients.push({ options, stop });
    return stop;
  },
}));

const event: RealtimeEvent = {
  type: "message.created",
  conversationId: "c1",
  messageId: "m1",
  channel: "MESSENGER",
  direction: "INBOUND",
  newConversation: false,
};

beforeEach(() => {
  vi.useFakeTimers();
  clients.length = 0;
});
afterEach(() => vi.useRealTimers());

describe("subscribeRealtime", () => {
  it("shares one client between subscribers and fans events out", () => {
    const a = vi.fn();
    const b = vi.fn();
    const offA = subscribeRealtime("org-1", { onEvent: a });
    const offB = subscribeRealtime("org-1", { onEvent: b });
    expect(clients).toHaveLength(1);
    clients[0].options.onEvent(event);
    expect(a).toHaveBeenCalledWith(event);
    expect(b).toHaveBeenCalledWith(event);
    offA();
    offB();
    vi.runAllTimers();
  });

  it("fans out reconnect and unauthorized signals", () => {
    const a = { onReconnect: vi.fn(), onUnauthorized: vi.fn() };
    const off = subscribeRealtime("org-1", a);
    clients[0].options.onReconnect();
    clients[0].options.onUnauthorized?.();
    expect(a.onReconnect).toHaveBeenCalledTimes(1);
    expect(a.onUnauthorized).toHaveBeenCalledTimes(1);
    off();
    vi.runAllTimers();
  });

  it("stays open while one subscriber remains and closes after the last leaves", () => {
    const offA = subscribeRealtime("org-1", {});
    const offB = subscribeRealtime("org-1", {});
    offA();
    vi.runAllTimers();
    expect(clients[0].stop).not.toHaveBeenCalled();
    offB();
    expect(clients[0].stop).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(clients[0].stop).toHaveBeenCalledTimes(1);
  });

  it("does not reconnect when re-subscribing right after unsubscribing", () => {
    subscribeRealtime("org-1", {})();
    const off = subscribeRealtime("org-1", {});
    vi.runAllTimers();
    expect(clients).toHaveLength(1);
    expect(clients[0].stop).not.toHaveBeenCalled();
    off();
    vi.runAllTimers();
  });

  it("opens a new client after a full close", () => {
    subscribeRealtime("org-1", {})();
    vi.runAllTimers();
    const off = subscribeRealtime("org-1", {});
    expect(clients).toHaveLength(2);
    off();
    vi.runAllTimers();
  });

  it("keeps organizations on separate connections", () => {
    const offA = subscribeRealtime("org-1", {});
    const offB = subscribeRealtime("org-2", {});
    expect(clients).toHaveLength(2);
    offA();
    vi.runAllTimers();
    expect(clients[0].stop).toHaveBeenCalledTimes(1);
    expect(clients[1].stop).not.toHaveBeenCalled();
    offB();
    vi.runAllTimers();
  });

  it("lets one throwing subscriber not stop the others", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const ok = vi.fn();
    const offA = subscribeRealtime("org-1", {
      onEvent: () => {
        throw new Error("boom");
      },
    });
    const offB = subscribeRealtime("org-1", { onEvent: ok });
    clients[0].options.onEvent(event);
    expect(ok).toHaveBeenCalledWith(event);
    offA();
    offB();
    vi.runAllTimers();
  });
});
