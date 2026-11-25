import { afterEach, describe, expect, it, vi } from "vitest";
import {
  browserFileUrl,
  getConversation,
  listConversations,
  updateReadState,
  listMessages,
  sendAttachment,
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

describe("listMessages attachments", () => {
  it("reads a message without attachments from an older backend as none", async () => {
    respond({ content: [{ id: "m1", text: "hi" }], page: 0, size: 30 });
    const page = await listMessages("tok", "o1", "c1", 0, 30);
    expect(page.content[0].attachments).toEqual([]);
  });
});

describe("sendAttachment", () => {
  it("posts the file as multipart without setting Content-Type", async () => {
    const fetchMock = respond({ id: "m1" });
    const file = new File(["x"], "receipt.png", { type: "image/png" });
    const message = await sendAttachment("tok", "o1", "c1", file);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(
      /\/api\/organizations\/o1\/conversations\/c1\/messages\/attachments$/,
    );
    expect(init.method).toBe("POST");
    expect(init.headers["Content-Type"]).toBeUndefined();
    expect(init.headers.Authorization).toBe("Bearer tok");
    expect((init.body as FormData).get("file")).toBeInstanceOf(File);
    expect(message.attachments).toEqual([]);
  });
});

describe("browserFileUrl", () => {
  const FILE = "11111111-2222-3333-4444-555555555555";
  afterEach(() => vi.unstubAllEnvs());

  it("loads our own files from the API address, whatever public address they were stored with", () => {
    vi.stubEnv("NEXT_PUBLIC_API_URL", "http://localhost:8080/");
    expect(
      browserFileUrl(`https://tunnel.ngrok-free.dev/api/files/${FILE}`),
    ).toBe(`http://localhost:8080/api/files/${FILE}`);
  });

  it("falls back to localhost:8080 when no public API address is set", () => {
    vi.stubEnv("NEXT_PUBLIC_API_URL", undefined);
    expect(browserFileUrl(`https://x.example/api/files/${FILE}`)).toBe(
      `http://localhost:8080/api/files/${FILE}`,
    );
  });

  it("leaves Meta links and anything that is not one of our files alone", () => {
    vi.stubEnv("NEXT_PUBLIC_API_URL", "http://localhost:8080");
    const meta = "https://scontent.xx.fbcdn.net/v/t1/photo.jpg?sig=abc";
    expect(browserFileUrl(meta)).toBe(meta);
    const other = `https://x.example/api/files/${FILE}/extra`;
    expect(browserFileUrl(other)).toBe(other);
    expect(browserFileUrl("not a url")).toBe("not a url");
  });

  it("leaves a shared Instagram post or story link untouched", () => {
    vi.stubEnv("NEXT_PUBLIC_API_URL", "http://localhost:8080");
    for (const link of [
      "https://www.instagram.com/p/C8xYz/",
      "https://www.instagram.com/stories/nadia/3400000000000000000/",
    ]) {
      expect(browserFileUrl(link)).toBe(link);
    }
  });

  it("is applied to the attachments of a message page", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_URL", "http://localhost:8080");
    respond({
      content: [
        {
          id: "m1",
          direction: "INBOUND",
          text: "[Photo]",
          sentAt: "2026-11-25T10:00:00Z",
          senderId: null,
          senderName: null,
          attachments: [
            {
              id: "a1",
              kind: "IMAGE",
              url: `https://tunnel.ngrok-free.dev/api/files/${FILE}`,
              fileName: null,
              contentType: "image/png",
            },
          ],
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    });
    const page = await listMessages("token", "org", "conv", 0, 20);
    expect(page.content[0].attachments[0].url).toBe(
      `http://localhost:8080/api/files/${FILE}`,
    );
  });
});
