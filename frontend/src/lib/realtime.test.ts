import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  backoffDelay,
  coalesce,
  isRelevant,
  parseRealtimeEvent,
  PING_INTERVAL_MS,
  realtimeUrl,
  startRealtimeClient,
  type SocketLike,
} from "./realtime";

const frame = (conversationId = "c1") =>
  JSON.stringify({
    type: "message.created",
    conversationId,
    messageId: "m1",
    channel: "MESSENGER",
    direction: "INBOUND",
    newConversation: false,
  });

class FakeSocket implements SocketLike {
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;
  sent: string[] = [];
  closed = false;
  constructor(readonly url: string) {}
  send(data: string) {
    this.sent.push(data);
  }
  close() {
    this.closed = true;
  }
}

describe("realtimeUrl", () => {
  it("maps http to ws and https to wss and carries the ticket", () => {
    expect(realtimeUrl("http://localhost:8080", "t 1")).toBe(
      "ws://localhost:8080/ws/inbox?ticket=t+1",
    );
    expect(realtimeUrl("https://api.example.com", "abc")).toBe(
      "wss://api.example.com/ws/inbox?ticket=abc",
    );
  });
});

describe("backoffDelay", () => {
  it("doubles, applies jitter and caps", () => {
    expect(backoffDelay(0, undefined, () => 1)).toBe(1000);
    expect(backoffDelay(2, undefined, () => 1)).toBe(4000);
    expect(backoffDelay(2, undefined, () => 0)).toBe(2000);
    expect(backoffDelay(20, undefined, () => 1)).toBe(30_000);
  });

  it("waits a minute when the session was replaced (4002)", () => {
    expect(backoffDelay(0, 4002, () => 1)).toBe(60_000);
  });
});

describe("parseRealtimeEvent", () => {
  it("accepts a message.created frame", () => {
    expect(parseRealtimeEvent(frame())).toMatchObject({
      conversationId: "c1",
      messageId: "m1",
      newConversation: false,
    });
  });

  it("ignores pong, malformed JSON, wrong types and non-text frames", () => {
    expect(parseRealtimeEvent('{"type":"pong"}')).toBeNull();
    expect(parseRealtimeEvent("{nope")).toBeNull();
    expect(parseRealtimeEvent("null")).toBeNull();
    expect(
      parseRealtimeEvent('{"type":"message.created","conversationId":1}'),
    ).toBeNull();
    expect(parseRealtimeEvent(new ArrayBuffer(1))).toBeNull();
  });
});

describe("isRelevant", () => {
  const event = parseRealtimeEvent(frame("c1"))!;
  it("matches everything for the list and only its own conversation for a thread", () => {
    expect(isRelevant(event)).toBe(true);
    expect(isRelevant(event, "c1")).toBe(true);
    expect(isRelevant(event, "c2")).toBe(false);
  });
});

describe("with fake timers", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("coalesces a burst into one call", () => {
    const fn = vi.fn();
    const c = coalesce(fn, 250);
    c.call();
    c.call();
    vi.advanceTimersByTime(100);
    c.call();
    vi.advanceTimersByTime(249);
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  describe("startRealtimeClient", () => {
    let sockets: FakeSocket[];
    let tickets: number;
    let fetchTicket: ReturnType<typeof vi.fn<() => Promise<string>>>;
    const onEvent = vi.fn();
    const onReconnect = vi.fn();

    const start = () =>
      startRealtimeClient({
        apiBase: "http://api.test",
        fetchTicket,
        createSocket: (url) => {
          const s = new FakeSocket(url);
          sockets.push(s);
          return s;
        },
        onEvent,
        onReconnect,
        random: () => 1,
      });
    const flush = () => vi.advanceTimersByTimeAsync(0);

    beforeEach(() => {
      sockets = [];
      tickets = 0;
      onEvent.mockReset();
      onReconnect.mockReset();
      fetchTicket = vi.fn(async () => `ticket-${++tickets}`);
    });

    it("connects with a ticket and delivers parsed events only", async () => {
      const stop = start();
      await flush();
      expect(sockets[0].url).toBe("ws://api.test/ws/inbox?ticket=ticket-1");
      sockets[0].onopen!();
      sockets[0].onmessage!({ data: frame("c1") });
      sockets[0].onmessage!({ data: "garbage" });
      sockets[0].onmessage!({ data: '{"type":"pong"}' });
      expect(onEvent).toHaveBeenCalledTimes(1);
      expect(onReconnect).not.toHaveBeenCalled();
      stop();
    });

    it("sends ping every 30 seconds", async () => {
      const stop = start();
      await flush();
      sockets[0].onopen!();
      vi.advanceTimersByTime(PING_INTERVAL_MS * 2);
      expect(sockets[0].sent).toEqual(["ping", "ping"]);
      stop();
    });

    it("reconnects with backoff and a fresh ticket, then reports the reconnect", async () => {
      const stop = start();
      await flush();
      sockets[0].onopen!();
      sockets[0].onclose!({ code: 1006 });
      await vi.advanceTimersByTimeAsync(999);
      expect(sockets).toHaveLength(1);
      await vi.advanceTimersByTimeAsync(1);
      expect(sockets[1].url).toContain("ticket=ticket-2");
      sockets[1].onopen!();
      expect(onReconnect).toHaveBeenCalledTimes(1);
      stop();
    });

    it("backs off longer after each failed connection and retries a failed ticket", async () => {
      fetchTicket.mockRejectedValueOnce(new Error("401"));
      const stop = start();
      await flush();
      expect(sockets).toHaveLength(0);
      await vi.advanceTimersByTimeAsync(1000);
      expect(sockets).toHaveLength(1);
      sockets[0].onclose!({ code: 1006 });
      await vi.advanceTimersByTimeAsync(1999);
      expect(sockets).toHaveLength(1);
      await vi.advanceTimersByTimeAsync(1);
      expect(sockets).toHaveLength(2);
      stop();
    });

    it("waits a minute after close code 4002", async () => {
      const stop = start();
      await flush();
      sockets[0].onopen!();
      sockets[0].onclose!({ code: 4002 });
      await vi.advanceTimersByTimeAsync(59_999);
      expect(sockets).toHaveLength(1);
      await vi.advanceTimersByTimeAsync(1);
      expect(sockets).toHaveLength(2);
      stop();
    });

    it("leaves no timers and no reconnect after stop", async () => {
      const stop = start();
      await flush();
      sockets[0].onopen!();
      sockets[0].onclose!({ code: 1006 });
      stop();
      expect(vi.getTimerCount()).toBe(0);
      expect(sockets[0].closed).toBe(true);
      await vi.advanceTimersByTimeAsync(120_000);
      expect(sockets).toHaveLength(1);
    });

    it("does not open a socket when stopped while the ticket is in flight", async () => {
      const stop = start();
      stop();
      await flush();
      expect(sockets).toHaveLength(0);
      expect(vi.getTimerCount()).toBe(0);
    });
  });
});
