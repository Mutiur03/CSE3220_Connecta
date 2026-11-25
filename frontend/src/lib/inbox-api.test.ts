import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getConversation,
  listConversations,
  updateReadState,
  listMessages,
  sendMessage,
  updateConversationStatus,
} from "./inbox-api";

function respond(body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("conversation thread api", () => {
  it("gets one conversation, bearer authenticated", async () => {
    const fetchMock = respond({ id: "c1" });
    await getConversation("tok", "o1", "c1");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/organizations\/o1\/conversations\/c1$/);
    expect(init.method).toBe("GET");
    expect(init.headers.Authorization).toBe("Bearer tok");
  });

  it("asks for a page of messages with page and size", async () => {
    const fetchMock = respond({ content: [] });
    await listMessages("tok", "o1", "c1", 2, 30);
    expect(fetchMock.mock.calls[0][0]).toMatch(
      /\/conversations\/c1\/messages\?page=2&size=30$/,
    );
  });

  it("patches the status as JSON", async () => {
    const fetchMock = respond({ id: "c1" });
    await updateConversationStatus("tok", "o1", "c1", "CLOSED");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/conversations\/c1$/);
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body)).toEqual({ status: "CLOSED" });
  });
});

describe("sendMessage", () => {
  it("posts the text to the conversation's messages, bearer authenticated", async () => {
    const fetchMock = respond({ id: "m1" });
    await sendMessage("tok", "o1", "c1", "hello");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(
      /\/api\/organizations\/o1\/conversations\/c1\/messages$/,
    );
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ text: "hello" });
    expect(init.headers.Authorization).toBe("Bearer tok");
  });
});

describe("conversation list and read state api", () => {
  it("asks for open conversations by default and none for the All tab", async () => {
    // A fresh body per call: a Response can be read once.
    const fetchMock = vi.fn().mockImplementation(
      async () =>
        new Response(JSON.stringify({ content: [] }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
    );
    vi.stubGlobal("fetch", fetchMock);
    await listConversations("tok", "o1", { q: "ana" }, 0, 20);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\?q=ana&size=20&status=OPEN$/);
    await listConversations("tok", "o1", { tab: "ALL" }, 1, 20);
    expect(fetchMock.mock.calls[1][0]).toMatch(/\?page=1&size=20$/);
    await listConversations("tok", "o1", { tab: "CLOSED" }, 0, 20);
    expect(fetchMock.mock.calls[2][0]).toMatch(/status=CLOSED$/);
  });

  it("puts the read state as JSON", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    await updateReadState("tok", "o1", "c1", false);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/conversations\/c1\/read-state$/);
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual({ unread: false });
  });
});
