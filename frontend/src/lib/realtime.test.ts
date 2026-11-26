import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  backoffDelay,
  coalesce,
  isRelevant,
  parseRealtimeEvent,
  PING_INTERVAL_MS,
  realtimeUrl,
  startRealtimeClient,
  MAX_CONSECUTIVE_UNAUTHORIZED,
  STABLE_OPEN_MS,
  TicketUnauthorizedError,
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

  it("accepts a participant.updated frame that carries only the conversation", () => {
    expect(
      parseRealtimeEvent(
        '{"type":"participant.updated","conversationId":"c1"}',
      ),
    ).toMatchObject({ type: "participant.updated", conversationId: "c1" });
    expect(parseRealtimeEvent('{"type":"participant.updated"}')).toBeNull();
  });

  it("accepts an inbox.refresh frame, which carries no conversation", () => {
    expect(
      parseRealtimeEvent('{"type":"inbox.refresh","conversationId":null}'),
    ).toMatchObject({ type: "inbox.refresh", conversationId: "" });
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

  it("an inbox refresh is relevant to the list and to every thread", () => {
    const refresh = parseRealtimeEvent('{"type":"inbox.refresh"}')!;
    expect(isRelevant(refresh)).toBe(true);
    expect(isRelevant(refresh, "c2")).toBe(true);
  });

  it("an assignment change reaches the list and only its own thread, with a null assignee too", () => {
    const assigned = parseRealtimeEvent(
      '{"type":"conversation.assigned","conversationId":"c1","assigneeMembershipId":null}',
    )!;
    expect(assigned).toMatchObject({
      type: "conversation.assigned",
      conversationId: "c1",
    });
    expect(isRelevant(assigned)).toBe(true);
    expect(isRelevant(assigned, "c1")).toBe(true);
    expect(isRelevant(assigned, "c2")).toBe(false);
    expect(parseRealtimeEvent('{"type":"conversation.assigned"}')).toBeNull();
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

    const start = (
      extra: Partial<Parameters<typeof startRealtimeClient>[0]> = {},
    ) =>
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
        ...extra,
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

    it("keeps backing off when the server accepts and closes at once", async () => {
      const stop = start();
      await flush();
      sockets[0].onopen!();
      sockets[0].onclose!({ code: 1006 });
      await vi.advanceTimersByTimeAsync(1000);
      sockets[1].onopen!();
      sockets[1].onclose!({ code: 1006 });
      await vi.advanceTimersByTimeAsync(1999);
      expect(sockets).toHaveLength(2);
      await vi.advanceTimersByTimeAsync(1);
      expect(sockets).toHaveLength(3);
      stop();
    });

    it("restarts the backoff after a connection stayed open long enough", async () => {
      const stop = start();
      await flush();
      sockets[0].onopen!();
      sockets[0].onclose!({ code: 1006 });
      await vi.advanceTimersByTimeAsync(1000);
      sockets[1].onopen!();
      await vi.advanceTimersByTimeAsync(STABLE_OPEN_MS);
      sockets[1].onclose!({ code: 1006 });
      await vi.advanceTimersByTimeAsync(1000);
      expect(sockets).toHaveLength(3);
      stop();
    });

    describe("ticket 401s", () => {
      const unauthorized = async () => {
        throw new TicketUnauthorizedError();
      };

      it("renews the session up to the cap, then gives up until the tab is shown", async () => {
        fetchTicket.mockImplementation(unauthorized);
        const onUnauthorized = vi.fn();
        let shown: () => void = () => {};
        const unsubscribe = vi.fn();
        const stop = start({
          onUnauthorized,
          subscribeShown: (cb) => {
            shown = cb;
            return unsubscribe;
          },
        });
        await vi.advanceTimersByTimeAsync(120_000);
        expect(onUnauthorized).toHaveBeenCalledTimes(
          MAX_CONSECUTIVE_UNAUTHORIZED,
        );
        expect(fetchTicket).toHaveBeenCalledTimes(
          MAX_CONSECUTIVE_UNAUTHORIZED + 1,
        );
        expect(vi.getTimerCount()).toBe(0);

        fetchTicket.mockImplementation(async () => "fresh");
        shown();
        await flush();
        expect(unsubscribe).toHaveBeenCalled();
        expect(sockets[0].url).toContain("ticket=fresh");
        stop();
      });

      it("resets the count after a successful ticket", async () => {
        const onUnauthorized = vi.fn();
        fetchTicket
          .mockImplementationOnce(unauthorized)
          .mockImplementationOnce(unauthorized)
          .mockImplementationOnce(async () => "ok");
        const stop = start({ onUnauthorized, subscribeShown: () => () => {} });
        await vi.advanceTimersByTimeAsync(10_000);
        expect(sockets).toHaveLength(1);
        sockets[0].onclose!({ code: 1006 });
        fetchTicket.mockImplementation(unauthorized);
        await vi.advanceTimersByTimeAsync(120_000);
        expect(onUnauthorized).toHaveBeenCalledTimes(
          2 + MAX_CONSECUTIVE_UNAUTHORIZED,
        );
        stop();
      });

      it("resets the count after a failure that is not a 401", async () => {
        const onUnauthorized = vi.fn();
        fetchTicket
          .mockImplementationOnce(unauthorized)
          .mockImplementationOnce(unauthorized)
          .mockRejectedValueOnce(new Error("503"))
          .mockImplementation(unauthorized);
        const stop = start({ onUnauthorized, subscribeShown: () => () => {} });
        await vi.advanceTimersByTimeAsync(120_000);
        expect(onUnauthorized).toHaveBeenCalledTimes(
          2 + MAX_CONSECUTIVE_UNAUTHORIZED,
        );
        stop();
      });

      it("stop removes the tab listener", async () => {
        fetchTicket.mockImplementation(unauthorized);
        const unsubscribe = vi.fn();
        const stop = start({ subscribeShown: () => unsubscribe });
        await vi.advanceTimersByTimeAsync(120_000);
        stop();
        expect(unsubscribe).toHaveBeenCalled();
      });
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
